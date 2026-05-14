package com.ecommerce.admin.controller;

import com.ecommerce.dto.RegionDto;
import com.ecommerce.service.RegionService;
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

@RestController
@RequestMapping("/api/admin/regions")
@RequiredArgsConstructor
@Tag(name = "Admin - Regions", description = "Geographic regions and their default currencies")
public class AdminRegionController {

    private final RegionService regionService;

    @GetMapping
    public Flux<RegionDto> list() {
        return regionService.listAll();
    }

    @GetMapping("/{id}")
    public Mono<RegionDto> get(@PathVariable UUID id) {
        return regionService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new region (country -> default currency mapping)")
    public Mono<RegionDto> create(@Valid @RequestBody RegionDto dto) {
        return regionService.create(dto);
    }

    @PutMapping("/{id}")
    public Mono<RegionDto> update(@PathVariable UUID id, @Valid @RequestBody RegionDto dto) {
        return regionService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable UUID id) {
        return regionService.delete(id);
    }
}
