package com.ecommerce.service.storage;

import com.ecommerce.config.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

/**
 * Filesystem storage - the default provider, behaviour-identical to the original
 * per-service disk code. PUBLIC objects live under {@code app.upload-dir} (served by
 * the {@code /uploads/**} static handler); PRIVATE objects under
 * {@code storage.local.private-dir} (never web-served; streamed via party checks).
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final String publicRoot;
    private final String privateRoot;

    public LocalStorageService(@Value("${app.upload-dir:uploads}") String publicRoot,
                               StorageProperties props) {
        this.publicRoot = publicRoot;
        this.privateRoot = props.getLocal().getPrivateDir();
    }

    @Override
    public Mono<Long> store(StorageArea area, String key, FilePart file, String contentType) {
        Path target = resolve(area, key);
        return Mono.fromCallable(() -> {
                    Files.createDirectories(target.getParent());
                    return target;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(path -> file.transferTo(path)
                        .subscribeOn(Schedulers.boundedElastic())
                        .thenReturn(path))
                .flatMap(path -> Mono.fromCallable(() -> Files.size(path))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @Override
    public Flux<DataBuffer> read(StorageArea area, String key) {
        Path path = resolve(area, key);
        return DataBufferUtils.readInputStream(
                () -> Files.newInputStream(path),
                new DefaultDataBufferFactory(),
                8192);
    }

    @Override
    public Mono<Void> delete(StorageArea area, String key) {
        Path path = resolve(area, key);
        return Mono.fromCallable(() -> Files.deleteIfExists(path))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Void> deletePrefix(StorageArea area, String prefix) {
        Path dir = resolve(area, prefix);
        return Mono.fromCallable(() -> {
                    if (Files.exists(dir)) {
                        try (var paths = Files.walk(dir)) {
                            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException e) {
                                    log.warn("Local storage: could not delete {}: {}", p, e.getMessage());
                                }
                            });
                        }
                    }
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public String publicUrl(String key) {
        return "/uploads/" + key;
    }

    /** Resolve a key under the area's root, guarding against path traversal. */
    private Path resolve(StorageArea area, String key) {
        Path root = Paths.get(area == StorageArea.PUBLIC ? publicRoot : privateRoot)
                .toAbsolutePath().normalize();
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Illegal storage key: " + key);
        }
        return resolved;
    }
}
