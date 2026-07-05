package com.ecommerce.service;

import com.ecommerce.dto.ProductMediaDto;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.model.ProductMedia;
import com.ecommerce.repository.ProductMediaRepository;
import com.ecommerce.service.storage.StorageArea;
import com.ecommerce.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductMediaService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;  // 10 MB
    private static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024; // 100 MB

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4", "video/webm", "video/quicktime");

    private final ProductMediaRepository mediaRepository;
    private final StorageService storage;

    public Mono<ProductMediaDto> upload(UUID productId, FilePart filePart) {
        String rawType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString() : "";
        // Strip parameters like "; charset=utf-8" for clean comparison
        String contentType = rawType.contains(";") ? rawType.substring(0, rawType.indexOf(';')).trim() : rawType;

        final String mediaType;
        if (ALLOWED_IMAGE_TYPES.contains(contentType)) {
            mediaType = "IMAGE";
        } else if (ALLOWED_VIDEO_TYPES.contains(contentType)) {
            mediaType = "VIDEO";
        } else {
            return Mono.error(new BadRequestException("Unsupported file type: " + contentType
                    + ". Allowed: JPEG, PNG, WEBP, GIF, MP4, WEBM, MOV"));
        }

        String originalName = filePart.filename();
        String ext = extension(originalName);
        String storedName = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        String key = "products/" + productId + "/" + storedName;
        final String finalContentType = contentType;

        return storage.store(StorageArea.PUBLIC, key, filePart, contentType)
                .flatMap(size -> {
                    long maxBytes = "VIDEO".equals(mediaType) ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
                    if (size > maxBytes) {
                        return storage.delete(StorageArea.PUBLIC, key)
                                .then(Mono.error(new BadRequestException("VIDEO".equals(mediaType)
                                        ? "Video exceeds 100 MB limit"
                                        : "Image exceeds 10 MB limit")));
                    }
                    return Mono.just(size);
                })
                .flatMap(size -> mediaRepository.countByProductId(productId)
                        .flatMap(count -> {
                            ProductMedia media = new ProductMedia();
                            media.setId(UUID.randomUUID());
                            media.setProductId(productId);
                            media.setFileName(storedName);
                            media.setOriginalName(originalName);
                            media.setMediaType(mediaType);
                            media.setUrl(storage.publicUrl(key));
                            media.setContentType(finalContentType);
                            media.setSizeBytes(size);
                            media.setSortOrder(count.intValue());
                            return mediaRepository.save(media);
                        }))
                .map(this::toDto);
    }

    public Flux<ProductMediaDto> findByProduct(UUID productId) {
        return mediaRepository.findByProductIdOrderBySortOrderAsc(productId)
                .map(this::toDto);
    }

    public Mono<Void> delete(UUID productId, UUID mediaId) {
        return mediaRepository.findById(mediaId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Media not found")))
                .filter(m -> productId.equals(m.getProductId()))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Media not found")))
                .flatMap(media -> storage.delete(StorageArea.PUBLIC,
                                "products/" + productId + "/" + media.getFileName())
                        .then(mediaRepository.delete(media)));
    }

    public Mono<Void> reorder(UUID productId, List<UUID> orderedIds) {
        return Flux.fromIterable(orderedIds)
                .index()
                .flatMap(tuple -> mediaRepository.updateSortOrder(
                        tuple.getT2(), productId, tuple.getT1().intValue()))
                .then();
    }

    private ProductMediaDto toDto(ProductMedia m) {
        return ProductMediaDto.builder()
                .id(m.getId())
                .productId(m.getProductId())
                .mediaType(m.getMediaType())
                .url(m.getUrl())
                .originalName(m.getOriginalName())
                .contentType(m.getContentType())
                .sizeBytes(m.getSizeBytes())
                .sortOrder(m.getSortOrder())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private static String extension(String filename) {
        if (filename == null || filename.isEmpty()) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    /** Cleanup helper: delete all of a product's media files when the product is deleted. */
    public Mono<Void> deleteAllForProduct(UUID productId) {
        return storage.deletePrefix(StorageArea.PUBLIC, "products/" + productId);
    }
}
