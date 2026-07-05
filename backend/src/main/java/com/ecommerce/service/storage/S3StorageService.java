package com.ecommerce.service.storage;

import com.ecommerce.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * S3/MinIO storage. Active only when {@code storage.provider=s3}. Uploads are
 * buffered to a temp file then streamed to the bucket (so size is known and the
 * event loop never blocks); reads stream straight from the object.
 *
 * NOTE: compile-verified but pending runtime validation against a live MinIO/S3 -
 * the default {@code local} provider is unaffected. Product media (PUBLIC) is served
 * via {@link #publicUrl} (a public-read object URL); dispute evidence (PRIVATE) is
 * streamed only through the party-checked controller endpoints.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.provider", havingValue = "s3")
public class S3StorageService implements StorageService {

    private final S3AsyncClient s3;
    private final StorageProperties props;
    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    private String bucket() {
        return props.getS3().getBucket();
    }

    @Override
    public Mono<Long> store(StorageArea area, String key, FilePart file, String contentType) {
        return Mono.fromCallable(() -> Files.createTempFile("upload-", ".tmp"))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(tmp -> file.transferTo(tmp)
                        .subscribeOn(Schedulers.boundedElastic())
                        .then(Mono.fromCallable(() -> Files.size(tmp)).subscribeOn(Schedulers.boundedElastic()))
                        .flatMap(size -> Mono.fromFuture(s3.putObject(
                                        PutObjectRequest.builder()
                                                .bucket(bucket()).key(key).contentType(contentType).build(),
                                        AsyncRequestBody.fromFile(tmp)))
                                .thenReturn(size))
                        .doFinally(signal -> deleteQuietly(tmp)));
    }

    @Override
    public Flux<DataBuffer> read(StorageArea area, String key) {
        return Mono.fromFuture(s3.getObject(
                        GetObjectRequest.builder().bucket(bucket()).key(key).build(),
                        AsyncResponseTransformer.toPublisher()))
                .flatMapMany(publisher -> Flux.from(publisher).map(bufferFactory::wrap));
    }

    @Override
    public Mono<Void> delete(StorageArea area, String key) {
        return Mono.fromFuture(s3.deleteObject(
                        DeleteObjectRequest.builder().bucket(bucket()).key(key).build()))
                .then();
    }

    @Override
    public Mono<Void> deletePrefix(StorageArea area, String prefix) {
        return Mono.fromFuture(s3.listObjectsV2(
                        ListObjectsV2Request.builder().bucket(bucket()).prefix(prefix).build()))
                .flatMapMany(response -> Flux.fromIterable(response.contents()))
                .flatMap(object -> Mono.fromFuture(s3.deleteObject(
                        DeleteObjectRequest.builder().bucket(bucket()).key(object.key()).build())))
                .then();
    }

    @Override
    public String publicUrl(String key) {
        String base = props.getS3().getPublicBaseUrl();
        return (base == null ? "" : base.replaceAll("/+$", "")) + "/" + key;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort temp cleanup
        }
    }
}
