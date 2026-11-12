package tz.co.chambaka.glyt.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tz.co.chambaka.glyt.config.AppProperties;

@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    public ObjectStore objectStore(AppProperties properties) {
        String type = normalize(properties.storage() == null ? null : properties.storage().type());
        ObjectStore store = switch (type) {
            case "r2", "s3", "minio" -> new S3ObjectStore(properties, type.equals("minio") ? "s3" : type);
            case "local" -> new LocalObjectStore(properties);
            default -> throw new IllegalStateException(
                    "Unsupported app.storage.type='" + type + "'. Use local, r2, or s3."
            );
        };
        if (store instanceof LocalObjectStore local) {
            log.info("Object storage: local ({})", local.root());
        } else if (store instanceof S3ObjectStore remote) {
            log.info(
                    "Object storage: {} bucket={} endpoint={}",
                    remote.backend(),
                    remote.bucket(),
                    remote.endpoint() == null ? "aws-default" : remote.endpoint()
            );
        }
        return store;
    }

    @Bean
    public HealthIndicator storageHealthIndicator(ObjectStore objectStore) {
        return () -> {
            var builder = Health.up().withDetail("backend", objectStore.backend());
            if (objectStore instanceof LocalObjectStore local) {
                builder.withDetail("uploadDir", local.root().toString());
            } else if (objectStore instanceof S3ObjectStore remote) {
                builder.withDetail("bucket", remote.bucket());
                if (remote.endpoint() != null) {
                    builder.withDetail("endpoint", remote.endpoint());
                }
            }
            return builder.build();
        };
    }

    static String normalize(String type) {
        if (type == null || type.isBlank()) {
            return "local";
        }
        return type.trim().toLowerCase();
    }
}
