package com.ecommerce.admin.controller;

import com.ecommerce.dto.DiscountTemplateDto;
import com.ecommerce.service.DiscountTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Admin discount-template CRUD. Templates are reusable blueprints; the
 * actual discount creation goes through {@link AdminDiscountController}
 * after the admin pre-fills the form from a template.
 */
@RestController
@RequestMapping("/api/admin/discount-templates")
@RequiredArgsConstructor
@Tag(name = "Admin - Discount Templates", description = "Reusable discount blueprints")
public class AdminDiscountTemplateController {

    private final DiscountTemplateService service;

    @GetMapping
    @Operation(summary = "List all discount templates (newest first)")
    public Flux<DiscountTemplateDto> list() {
        return service.listAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a discount template by ID")
    public Mono<DiscountTemplateDto> get(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save a new discount template")
    public Mono<DiscountTemplateDto> create(@Valid @RequestBody DiscountTemplateDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a discount template")
    public Mono<DiscountTemplateDto> update(@PathVariable UUID id,
                                             @Valid @RequestBody DiscountTemplateDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a discount template permanently")
    public Mono<Void> delete(@PathVariable UUID id) {
        return service.delete(id);
    }
}
