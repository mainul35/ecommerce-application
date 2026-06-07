package com.ecommerce.service;

import com.ecommerce.exception.BadRequestException;
import com.ecommerce.model.DisputeAttachment;
import com.ecommerce.repository.DisputeAttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * Stores image/video evidence attached to dispute messages, mirroring the
 * ProductMediaService upload rules (type allow-list + size caps). Files land
 * under {upload-dir}/disputes/{disputeId}/ and are served at
 * /uploads/disputes/** (authenticated - see AccessRules).
 */
@Service
@RequiredArgsConstructor
public class DisputeMediaService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;  // 10 MB
    private static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024; // 100 MB

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4", "video/webm", "video/quicktime");

    private final DisputeAttachmentRepository attachmentRepository;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    public Mono<DisputeAttachment> store(UUID disputeId, UUID messageId, FilePart filePart) {
        String rawType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString() : "";
        String contentType = rawType.contains(";") ? rawType.substring(0, rawType.indexOf(';')).trim() : rawType;

        final DisputeAttachment.AttachmentType attachmentType;
        if (ALLOWED_IMAGE_TYPES.contains(contentType)) {
            attachmentType = DisputeAttachment.AttachmentType.IMAGE;
        } else if (ALLOWED_VIDEO_TYPES.contains(contentType)) {
            attachmentType = DisputeAttachment.AttachmentType.VIDEO;
        } else {
            return Mono.error(new BadRequestException("Unsupported file type: " + contentType
                    + ". Allowed: JPEG, PNG, WEBP, GIF, MP4, WEBM, MOV"));
        }

        String originalName = filePart.filename();
        String ext = extension(originalName);
        String storedName = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        Path dir = Paths.get(uploadDir, "disputes", disputeId.toString());
        Path filePath = dir.resolve(storedName);
        String url = "/uploads/disputes/" + disputeId + "/" + storedName;
        final String finalContentType = contentType;

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
                    long maxBytes = attachmentType == DisputeAttachment.AttachmentType.VIDEO
                            ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
                    if (size > maxBytes) {
                        Files.delete(path);
                        throw new BadRequestException(attachmentType == DisputeAttachment.AttachmentType.VIDEO
                                ? "Video exceeds 100 MB limit"
                                : "Image exceeds 10 MB limit");
                    }
                    return size;
                }).subscribeOn(Schedulers.boundedElastic()))
                .flatMap(size -> attachmentRepository.save(DisputeAttachment.builder()
                        .id(UUID.randomUUID())
                        .disputeMessageId(messageId)
                        .fileName(storedName)
                        .originalName(originalName)
                        .url(url)
                        .contentType(finalContentType)
                        .attachmentType(attachmentType)
                        .sizeBytes(size)
                        .build()));
    }

    private static String extension(String filename) {
        if (filename == null || filename.isEmpty()) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
