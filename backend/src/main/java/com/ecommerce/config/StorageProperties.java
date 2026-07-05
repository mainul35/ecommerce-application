package com.ecommerce.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Storage configuration. {@code storage.provider} = {@code local} (default) or
 * {@code s3}. The PUBLIC local root reuses {@code app.upload-dir} so the existing
 * {@code /uploads/**} static handler keeps serving product media unchanged.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /** local | s3 */
    private String provider = "local";

    private final Local local = new Local();
    private final S3 s3 = new S3();

    @Getter
    @Setter
    public static class Local {
        /** Root for PRIVATE evidence (dispute files), streamed via party-checked endpoints. */
        private String privateDir = "private-storage";
    }

    @Getter
    @Setter
    public static class S3 {
        /** S3 endpoint. For MinIO, e.g. http://minio:9000. Leave blank for real AWS S3. */
        private String endpoint;
        private String region = "us-east-1";
        private String bucket = "ecommerce-media";
        private String accessKey;
        private String secretKey;
        /** MinIO requires path-style addressing (bucket in the path, not the host). */
        private boolean pathStyleAccess = true;
        /**
         * Absolute base URL for serving PUBLIC objects, e.g.
         * https://cdn.example.com/ecommerce-media. Product media URLs are built as
         * {@code {publicBaseUrl}/{key}}.
         */
        private String publicBaseUrl;
    }
}
