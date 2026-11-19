package tz.co.chambaka.glyt.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.support.TestFixtures;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorageConfigTest {

    @TempDir
    Path tmp;

    private final StorageConfig config = new StorageConfig();

    private AppProperties withStorage(AppProperties.Storage storage) {
        AppProperties base = TestFixtures.appProperties();
        return new AppProperties(base.jwt(), base.cors(), storage, base.ops(), base.seed(), base.mail(), base.payments());
    }

    private AppProperties.Storage storage(String type) {
        return new AppProperties.Storage(type, tmp.toString(), "", "bucket", "k", "s", "auto", true);
    }

    @Test
    void normalizeDefaultsToLocal() {
        assertThat(StorageConfig.normalize(null)).isEqualTo("local");
        assertThat(StorageConfig.normalize("")).isEqualTo("local");
        assertThat(StorageConfig.normalize("   ")).isEqualTo("local");
    }

    @Test
    void normalizeTrimsAndLowercases() {
        assertThat(StorageConfig.normalize("  S3 ")).isEqualTo("s3");
        assertThat(StorageConfig.normalize("LOCAL")).isEqualTo("local");
    }

    @ParameterizedTest
    @CsvSource({"local", "LOCAL", "' Local '"})
    void createsLocalStore(String type) {
        ObjectStore store = config.objectStore(withStorage(storage(type)));

        assertThat(store).isInstanceOf(LocalObjectStore.class);
        assertThat(store.backend()).isEqualTo("local");
    }

    @Test
    void blankTypeFallsBackToLocal() {
        assertThat(config.objectStore(withStorage(storage(""))))
                .isInstanceOf(LocalObjectStore.class);
    }

    @Test
    void unsupportedTypeIsRejected() {
        assertThatThrownBy(() -> config.objectStore(withStorage(storage("ftp"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported app.storage.type='ftp'");
    }

    @Test
    void healthIndicatorForLocalStoreReportsUploadDir() {
        LocalObjectStore local = new LocalObjectStore(withStorage(storage("local")));

        Health health = config.storageHealthIndicator(local).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("backend", "local");
        assertThat(health.getDetails()).containsEntry("uploadDir", local.root().toString());
    }

    @Test
    void healthIndicatorForOtherBackendOnlyReportsBackend() {
        ObjectStore other = mock(ObjectStore.class);
        when(other.backend()).thenReturn("custom");

        HealthIndicator indicator = config.storageHealthIndicator(other);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsOnlyKeys("backend").containsEntry("backend", "custom");
    }
}
