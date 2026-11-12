package tz.co.chambaka.glyt.storage;

import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.web.ApiException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalObjectStore implements ObjectStore {

    private final Path root;

    public LocalObjectStore(AppProperties properties) {
        String uploadDir = properties.storage().uploadDir();
        if (uploadDir == null || uploadDir.isBlank()) {
            uploadDir = "data/uploads";
        }
        Path path = Path.of(uploadDir);
        if (!path.isAbsolute()) {
            path = Path.of("").toAbsolutePath().resolve(path);
        }
        this.root = path.normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException ex) {
            throw new IllegalStateException("Local upload directory could not be created: " + this.root, ex);
        }
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        Path file = resolve(key);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The file could not be saved.");
        }
    }

    @Override
    public byte[] get(String key) {
        Path file = resolve(key);
        if (!Files.exists(file)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File is missing.");
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The file could not be read.");
        }
    }

    @Override
    public String backend() {
        return "local";
    }

    public Path root() {
        return root;
    }

    private Path resolve(String key) {
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File path is not valid.");
        }
        return file;
    }
}
