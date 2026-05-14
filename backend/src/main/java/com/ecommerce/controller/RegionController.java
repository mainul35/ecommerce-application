package com.ecommerce.controller;

import com.ecommerce.dto.RegionDto;
import com.ecommerce.service.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Public read access to regions. The storefront calls
 * {@code /by-country/{cc}} after IP geolocation to discover the customer's
 * region (and hence default currency) without exposing the admin UUID surface.
 */
@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
@Tag(name = "Regions", description = "Public region lookup")
public class RegionController {

    private final RegionService regionService;

    @GetMapping
    @Operation(summary = "List all active regions")
    public Flux<RegionDto> list() {
        return regionService.listActive();
    }

    @GetMapping("/by-country/{countryCode}")
    @Operation(summary = "Find the region for an ISO 3166 country code (returns 404 if not configured)")
    public Mono<ResponseEntity<RegionDto>> byCountry(@PathVariable String countryCode) {
        return regionService.findByCountryCode(countryCode)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
