package com.ecommerce.service;

import com.ecommerce.dto.DiscountTemplateDto;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.DiscountTemplateMapper;
import com.ecommerce.model.Discount;
import com.ecommerce.model.DiscountTemplate;
import com.ecommerce.repository.DiscountTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * CRUD for reusable discount blueprints. The frontend pre-fills the new-discount
 * form from a template; no atomic "instantiate" endpoint needed (the create flow
 * for {@link Discount} already handles the actual persistence).
 */
@Service
@RequiredArgsConstructor
public class DiscountTemplateService {

    private static final String NOT_FOUND = "Discount template not found";

    private final DiscountTemplateRepository repository;
    private final DiscountTemplateMapper mapper;

    public Flux<DiscountTemplateDto> listAll() {
        return repository.findAllByOrderByCreatedAtDesc().map(mapper::toDto);
    }

    public Mono<DiscountTemplateDto> findById(UUID id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .map(mapper::toDto);
    }

    public Mono<DiscountTemplateDto> create(DiscountTemplateDto dto) {
        validate(dto);
        DiscountTemplate entity = mapper.toEntity(dto);
        entity.setId(UUID.randomUUID());
        return repository.save(entity).map(mapper::toDto);
    }

    public Mono<DiscountTemplateDto> update(UUID id, DiscountTemplateDto dto) {
        validate(dto);
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .flatMap(existing -> {
                    existing.setName(dto.getName());
                    existing.setDescription(dto.getDescription());
                    existing.setType(dto.getType());
                    existing.setValue(dto.getValue());
                    existing.setDefaultDurationDays(dto.getDefaultDurationDays());
                    return repository.save(existing);
                })
                .map(mapper::toDto);
    }

    public Mono<Void> delete(UUID id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .flatMap(repository::delete);
    }

    private void validate(DiscountTemplateDto dto) {
        if (dto.getType() == Discount.DiscountType.PERCENTAGE
                && dto.getValue() != null
                && dto.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException("Percentage discount must be at most 100");
        }
    }
}
