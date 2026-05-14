package com.ecommerce.service;

import com.ecommerce.dto.RegionDto;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.RegionMapper;
import com.ecommerce.model.Region;
import com.ecommerce.repository.CurrencyRepository;
import com.ecommerce.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegionService {

    private static final String NOT_FOUND = "Region not found";

    private final RegionRepository regionRepository;
    private final CurrencyRepository currencyRepository;
    private final RegionMapper regionMapper;

    public Flux<RegionDto> listAll() {
        return regionRepository.findAllByOrderByNameAsc().map(regionMapper::toDto);
    }

    public Flux<RegionDto> listActive() {
        return regionRepository.findByIsActiveTrueOrderByNameAsc().map(regionMapper::toDto);
    }

    public Mono<RegionDto> findById(UUID id) {
        return regionRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .map(regionMapper::toDto);
    }

    /**
     * Look up a region by ISO 3166-1 alpha-2 country code. Used by the
     * storefront after IP geolocation: "what region/currency does the
     * detected country map to?". Returns empty Mono if no configured region
     * matches (caller falls back to the base currency).
     */
    public Mono<RegionDto> findByCountryCode(String countryCode) {
        return regionRepository.findByCountryCodeIgnoreCase(countryCode)
                .map(regionMapper::toDto);
    }

    public Mono<RegionDto> create(RegionDto dto) {
        return ensureCurrencyExists(dto.getCurrencyCode()).then(Mono.defer(() -> {
            Region entity = regionMapper.toEntity(dto);
            entity.setId(UUID.randomUUID());
            entity.setCountryCode(dto.getCountryCode().toUpperCase());
            entity.setCurrencyCode(dto.getCurrencyCode().toUpperCase());
            if (entity.getIsActive() == null) entity.setIsActive(true);
            return regionRepository.save(entity);
        })).map(regionMapper::toDto);
    }

    public Mono<RegionDto> update(UUID id, RegionDto dto) {
        return regionRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .flatMap(existing -> ensureCurrencyExists(dto.getCurrencyCode()).then(Mono.defer(() -> {
                    existing.setName(dto.getName());
                    existing.setCountryCode(dto.getCountryCode().toUpperCase());
                    existing.setCurrencyCode(dto.getCurrencyCode().toUpperCase());
                    if (dto.getIsActive() != null) existing.setIsActive(dto.getIsActive());
                    return regionRepository.save(existing);
                })))
                .map(regionMapper::toDto);
    }

    public Mono<Void> delete(UUID id) {
        return regionRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .flatMap(regionRepository::delete);
    }

    private Mono<Void> ensureCurrencyExists(String code) {
        return currencyRepository.findById(code.toUpperCase())
                .switchIfEmpty(Mono.error(new BadRequestException(
                        "Currency '" + code + "' is not configured. Add it under Currencies first.")))
                .then();
    }
}
