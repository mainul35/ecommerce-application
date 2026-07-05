package com.ecommerce.service;

import com.ecommerce.exception.BadRequestException;
import com.ecommerce.model.DisputeAttachment;
import com.ecommerce.repository.DisputeAttachmentRepository;
import com.ecommerce.service.storage.StorageArea;
import com.ecommerce.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

/**
 * Stores image/video evidence attached to dispute messages (type allow-list + size
 * caps), through the {@link StorageService} PRIVATE area.
 *
 * SECURITY: evidence is sensitive, so it lives in PRIVATE storage (never the public
 * /uploads static handler) and is only ever streamed back through the party-checked
 * endpoint {@code GET /api/disputes/{disputeId}/attachments/{attachmentId}/file}
 * (see DisputeService#attachmentForViewer). Keys are {@code disputes/{disputeId}/{file}}.
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
    private final StorageService storage;

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
        // Pre-generate the attachment id so the stored URL points straight at the
        // party-checked streaming endpoint for this exact row.
        UUID attachmentId = UUID.randomUUID();
        String key = key(disputeId, storedName);
        String url = "/api/disputes/" + disputeId + "/attachments/" + attachmentId + "/file";
        final String finalContentType = contentType;

        return storage.store(StorageArea.PRIVATE, key, filePart, contentType)
                .flatMap(size -> {
                    long maxBytes = attachmentType == DisputeAttachment.AttachmentType.VIDEO
                            ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
                    if (size > maxBytes) {
                        return storage.delete(StorageArea.PRIVATE, key)
                                .then(Mono.error(new BadRequestException(
                                        attachmentType == DisputeAttachment.AttachmentType.VIDEO
                                                ? "Video exceeds 100 MB limit"
                                                : "Image exceeds 10 MB limit")));
                    }
                    return Mono.just(size);
                })
                .flatMap(size -> attachmentRepository.save(DisputeAttachment.builder()
                        .id(attachmentId)
                        .disputeMessageId(messageId)
                        .fileName(storedName)
                        .originalName(originalName)
                        .url(url)
                        .contentType(finalContentType)
                        .attachmentType(attachmentType)
                        .sizeBytes(size)
                        .build()));
    }

    /** Stream a stored file's bytes (for the party-checked viewer endpoint). */
    public Flux<DataBuffer> read(UUID disputeId, String fileName) {
        return storage.read(StorageArea.PRIVATE, key(disputeId, fileName));
    }

    private static String key(UUID disputeId, String fileName) {
        return "disputes/" + disputeId + "/" + fileName;
    }

    private static String extension(String filename) {
        if (filename == null || filename.isEmpty()) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
