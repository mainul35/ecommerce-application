package com.ecommerce.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.net.URI;

/**
 * S3 async client, created only when {@code storage.provider=s3}. Works against
 * MinIO (via {@code storage.s3.endpoint} + path-style) or real AWS S3 (leave the
 * endpoint blank). Never instantiated for the default local provider.
 */
@Configuration
@ConditionalOnProperty(name = "storage.provider", havingValue = "s3")
public class S3Config {

    @Bean(destroyMethod = "close")
    S3AsyncClient s3AsyncClient(StorageProperties props) {
        StorageProperties.S3 s3 = props.getS3();
        var builder = S3AsyncClient.builder()
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey())))
                .forcePathStyle(s3.isPathStyleAccess());
        if (s3.getEndpoint() != null && !s3.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3.getEndpoint()));
        }
        return builder.build();
    }
}
