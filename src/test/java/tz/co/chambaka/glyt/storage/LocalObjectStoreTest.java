package tz.co.chambaka.glyt.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.support.TestFixtures;
import tz.co.chambaka.glyt.web.ApiException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalObjectStoreTest {

    @TempDir
    Path tmp;

    private static AppProperties withUploadDir(String uploadDir) {
        AppProperties base = TestFixtures.appProperties();
        AppProperties.Storage s = base.storage();
        return new AppProperties(
                base.jwt(), base.cors(),
                new AppProperties.Storage(s.type(), uploadDir, s.endpoint(), s.bucket(), s.accessKey(), s.secretKey(), s.region(), s.pathStyle()),
                base.ops(), base.seed(), base.mail(), base.payments());
    }

    private LocalObjectStore store() {
        return new LocalObjectStore(withUploadDir(tmp.toString()));
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void backendIsLocal() {
        assertThat(store().backend()).isEqualTo("local");
    }

    @Test
    void implementsObjectStore() {
        ObjectStore store = store();
        assertThat(store.backend()).isEqualTo("local");
    }

    @Test
    void createsRootDirectoryIfMissing() {
        Path nested = tmp.resolve("a/b/c");

        LocalObjectStore store = new LocalObjectStore(withUploadDir(nested.toString()));

        assertThat(Files.isDirectory(nested)).isTrue();
        assertThat(store.root()).isEqualTo(nested.toAbsolutePath().normalize());
    }

    @Test
    void normalizesRootPath() {
        LocalObjectStore store = new LocalObjectStore(withUploadDir(tmp + "/x/../y"));

        assertThat(store.root()).isEqualTo(tmp.resolve("y").normalize());
    }

    @Test
    void relativeUploadDirIsResolvedAgainstWorkingDirectory() throws IOException {
        Path expected = Path.of("").toAbsolutePath().resolve("target/test-uploads-relative").normalize();
        try {
            LocalObjectStore store = new LocalObjectStore(withUploadDir("target/test-uploads-relative"));

            assertThat(store.root()).isEqualTo(expected);
            assertThat(store.root().isAbsolute()).isTrue();
        } finally {
            deleteRecursively(expected);
        }
    }

    @Test
    void blankUploadDirFallsBackToDefault() throws IOException {
        Path dataDir = Path.of("").toAbsolutePath().resolve("data");
        Path expected = dataDir.resolve("uploads");
        boolean dataExisted = Files.exists(dataDir);
        boolean uploadsExisted = Files.exists(expected);
        try {
            LocalObjectStore blank = new LocalObjectStore(withUploadDir("  "));
            LocalObjectStore nul = new LocalObjectStore(withUploadDir(null));

            assertThat(blank.root()).isEqualTo(expected);
            assertThat(nul.root()).isEqualTo(expected);
        } finally {
            if (!uploadsExisted) {
                deleteRecursively(expected);
            }
            if (!dataExisted && Files.isDirectory(dataDir)) {
                try (Stream<Path> children = Files.list(dataDir)) {
                    if (children.findAny().isEmpty()) {
                        Files.delete(dataDir);
                    }
                }
            }
        }
    }

    @Test
    void constructorFailsWhenDirectoryCannotBeCreated() throws IOException {
        Path file = Files.writeString(tmp.resolve("not-a-dir"), "x");

        assertThatThrownBy(() -> new LocalObjectStore(withUploadDir(file.resolve("child").toString())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("could not be created")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void putThenGetRoundTrips() {
        LocalObjectStore store = store();
        byte[] data = "photo-bytes".getBytes(StandardCharsets.UTF_8);

        store.put("bags/1/front.jpg", data, "image/jpeg");

        assertThat(store.get("bags/1/front.jpg")).isEqualTo(data);
        assertThat(Files.exists(tmp.resolve("bags/1/front.jpg"))).isTrue();
    }

    @Test
    void putCreatesNestedDirectories() {
        LocalObjectStore store = store();

        store.put("a/b/c/d.bin", new byte[]{1, 2, 3}, "application/octet-stream");

        assertThat(store.get("a/b/c/d.bin")).containsExactly(1, 2, 3);
    }

    @Test
    void putOverwritesExistingObject() {
        LocalObjectStore store = store();
        store.put("k.txt", "one".getBytes(StandardCharsets.UTF_8), "text/plain");
        store.put("k.txt", "two".getBytes(StandardCharsets.UTF_8), "text/plain");

        assertThat(new String(store.get("k.txt"), StandardCharsets.UTF_8)).isEqualTo("two");
    }

    @Test
    void putAcceptsEmptyContentAndNullContentType() {
        LocalObjectStore store = store();

        store.put("empty.bin", new byte[0], null);

        assertThat(store.get("empty.bin")).isEmpty();
    }

    @Test
    void getMissingObjectIsNotFound() {
        assertThatThrownBy(() -> store().get("missing.jpg"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).isEqualTo("File is missing.");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"../evil.txt", "a/../../evil.txt", "../../etc/passwd"})
    void putRejectsPathTraversal(String key) {
        LocalObjectStore store = store();

        assertThatThrownBy(() -> store.put(key, new byte[]{1}, "text/plain"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("File path is not valid.");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"../evil.txt", "a/../../evil.txt"})
    void getRejectsPathTraversal(String key) {
        LocalObjectStore store = store();

        assertThatThrownBy(() -> store.get(key))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void traversalThatStaysInsideRootIsAllowed() {
        LocalObjectStore store = store();

        store.put("a/../b.txt", "ok".getBytes(StandardCharsets.UTF_8), "text/plain");

        assertThat(new String(store.get("b.txt"), StandardCharsets.UTF_8)).isEqualTo("ok");
    }

    @Test
    void putFailsWithServerErrorWhenParentIsAFile() {
        LocalObjectStore store = store();
        store.put("blocker", new byte[]{1}, "x");

        assertThatThrownBy(() -> store.put("blocker/child.txt", new byte[]{2}, "x"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(ex.getMessage()).isEqualTo("The file could not be saved.");
                });
    }

    @Test
    void putFailsWithServerErrorWhenTargetIsADirectory() {
        LocalObjectStore store = store();
        store.put("dir/file.txt", new byte[]{1}, "x");

        assertThatThrownBy(() -> store.put("dir", new byte[]{2}, "x"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    void getFailsWithServerErrorWhenTargetIsADirectory() {
        LocalObjectStore store = store();
        store.put("dir/file.txt", new byte[]{1}, "x");

        assertThatThrownBy(() -> store.get("dir"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(ex.getMessage()).isEqualTo("The file could not be read.");
                });
    }
}
