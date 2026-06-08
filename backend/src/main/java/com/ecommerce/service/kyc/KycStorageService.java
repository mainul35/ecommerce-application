package com.ecommerce.service.kyc;

import com.ecommerce.config.KycProperties;
import com.ecommerce.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
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
import java.util.Set;
import java.util.UUID;

/**
 * File I/O for KYC evidence. Files live under {kyc.storage-dir}/{caseId}/ -
 * a PRIVATE directory that is never registered as a web resource location
 * (unlike app.upload-dir). Reads happen only through party-checked
 * controller endpoints; deletes happen on decision or retention expiry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KycStorageService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024; // 10 MB

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    private final KycProperties props;

    public record StoredFile(String fileName, String contentType, long sizeBytes) {
    }

    /** Persist an uploaded evidence image; returns its randomized stored name. */
    public Mono<StoredFile> store(UUID caseId, FilePart filePart) {
        String rawType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString() : "";
        String contentType = rawType.contains(";")
                ? rawType.substring(0, rawType.indexOf(';')).trim() : rawType;
        if (!ALLOWED_TYPES.contains(contentType)) {
            return Mono.error(new BadRequestException(
                    "Unsupported file type: " + contentType + ". Allowed: JPEG, PNG, WEBP"));
        }

        String ext = switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
        String storedName = UUID.randomUUID() + "." + ext;
        Path dir = caseDir(caseId);
        Path filePath = dir.resolve(storedName);

        return Mono.fromCallable(() -> {
                    Files.createDirectories(dir);
                    return filePath;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(path -> filePart.transferTo(path)
                        .subscribeOn(Schedulers.boundedElastic())
                        .thenReturn(path))
                .flatMap(path -> Mono.fromCallable(() -> {
                    long size = Files.size(path);
                    if (size > MAX_IMAGE_BYTES) {
                        Files.delete(path);
                        throw new BadRequestException("Image exceeds 10 MB limit");
                    }
                    return new StoredFile(storedName, contentType, size);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    /** Stream a stored file's bytes (for the party-checked viewer endpoints). */
    public Flux<DataBuffer> read(UUID caseId, String fileName) {
        Path path = resolve(caseId, fileName);
        return DataBufferUtils.readInputStream(
                () -> Files.newInputStream(path),
                new org.springframework.core.io.buffer.DefaultDataBufferFactory(),
                8192);
    }

    public Path resolve(UUID caseId, String fileName) {
        return caseDir(caseId).resolve(fileName);
    }

    /** Remove one stored file (slot re-upload). */
    public Mono<Void> delete(UUID caseId, String fileName) {
        return Mono.fromCallable(() -> {
                    Files.deleteIfExists(resolve(caseId, fileName));
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /** Remove every evidence file of a case - the purge primitive. */
    public Mono<Void> deleteCaseDir(UUID caseId) {
        return Mono.fromCallable(() -> {
                    Path dir = caseDir(caseId);
                    if (Files.exists(dir)) {
                        try (var paths = Files.walk(dir)) {
                            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException e) {
                                    log.warn("KYC purge: could not delete {}: {}", p, e.getMessage());
                                }
                            });
                        }
                    }
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(v -> log.info("KYC evidence directory purged for case {}", caseId))
                .then();
    }

    private Path caseDir(UUID caseId) {
        return Paths.get(props.getStorageDir(), caseId.toString());
    }
}
