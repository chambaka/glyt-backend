package tz.co.chambaka.glyt.storage;

import org.springframework.http.HttpStatus;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.web.ApiException;

import java.net.URI;

/**
 * S3-compatible object store used for Cloudflare R2, MinIO, and AWS S3.
 * R2: endpoint {@code https://<ACCOUNT_ID>.r2.cloudflarestorage.com}, region {@code auto}.
 */
public class S3ObjectStore implements ObjectStore {

    private final S3Client client;
    private final String bucket;
    private final String endpoint;
    private final String backend;

    public S3ObjectStore(AppProperties properties, String backend) {
        var storage = properties.storage();
        this.backend = backend == null || backend.isBlank() ? "s3" : backend.trim().toLowerCase();

        require(storage.bucket(), "app.storage.bucket");
        require(storage.accessKey(), "app.storage.access-key");
        require(storage.secretKey(), "app.storage.secret-key");

        boolean r2 = "r2".equals(this.backend);
        this.endpoint = blankToNull(storage.endpoint());
        if (r2 && this.endpoint == null) {
            throw new IllegalStateException(
                    "app.storage.endpoint is required for Cloudflare R2 "
                            + "(https://<ACCOUNT_ID>.r2.cloudflarestorage.com)."
            );
        }

        String region = blankToNull(storage.region());
        if (region == null) {
            region = r2 || this.endpoint != null ? "auto" : "us-east-1";
        }

        this.bucket = storage.bucket().trim();

        boolean pathStyle = storage.pathStyle() != null
                ? storage.pathStyle()
                : this.endpoint != null; // R2 / MinIO custom endpoints

        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(storage.accessKey().trim(), storage.secretKey().trim())
                ))
                // AWS SDK default flexible checksums break many S3-compatible APIs including R2.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);

        if (this.endpoint != null) {
            builder.endpointOverride(URI.create(this.endpoint))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(pathStyle)
                            .build());
        }

        this.client = builder.build();
        ensureBucket();
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        try {
            client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromBytes(bytes)
            );
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The file could not be saved.");
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File is missing.");
        }
    }

    @Override
    public String backend() {
        return backend;
    }

    public String bucket() {
        return bucket;
    }

    public String endpoint() {
        return endpoint;
    }

    private void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return;
        } catch (NoSuchBucketException missing) {
            // fall through to create
        } catch (RuntimeException ex) {
            // Some R2 tokens cannot HeadBucket; try create then re-check below.
        }
        try {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (RuntimeException createEx) {
            try {
                client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            } catch (RuntimeException headEx) {
                throw new IllegalStateException(
                        "Object storage bucket '" + bucket + "' is not reachable"
                                + (endpoint == null ? "" : " at " + endpoint)
                                + ". For Cloudflare R2, create the bucket in the dashboard and set "
                                + "APP_STORAGE_ENDPOINT / APP_STORAGE_ACCESS_KEY / APP_STORAGE_SECRET_KEY.",
                        createEx
                );
            }
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when using S3-compatible storage (r2/s3/minio).");
        }
    }
}
