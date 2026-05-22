package com.ecommerce.admin.controller;

import com.ecommerce.dto.ProductMediaDto;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.service.ProductMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/products/{productId}/media")
@RequiredArgsConstructor
public class AdminProductMediaController {

    private final ProductMediaService mediaService;
    private final ProductRepository productRepository;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ProductMediaDto> upload(
            @PathVariable UUID productId,
            @RequestPart("file") FilePart filePart) {
        return productRepository.existsById(productId)
                .filter(Boolean.TRUE::equals)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product not found")))
                .flatMap(__ -> mediaService.upload(productId, filePart));
    }

    @GetMapping
    public Flux<ProductMediaDto> list(@PathVariable UUID productId) {
        return mediaService.findByProduct(productId);
    }

    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(
            @PathVariable UUID productId,
            @PathVariable UUID mediaId) {
        return mediaService.delete(productId, mediaId);
    }

    /** Reorder: body is an ordered list of media UUIDs; sort_order is set to list index. */
    @PutMapping("/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> reorder(
            @PathVariable UUID productId,
            @RequestBody List<UUID> orderedIds) {
        return mediaService.reorder(productId, orderedIds);
    }
}
