package com.ecommerce.service;

import com.ecommerce.model.Category;
import com.ecommerce.dto.CategoryDto;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.CategoryMapper;
import com.ecommerce.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public Flux<CategoryDto> findAll() {
        return categoryRepository.findByIsActiveTrue()
                .map(categoryMapper::toDto);
    }

    public Flux<CategoryDto> findRootCategories() {
        return categoryRepository.findByParentIdIsNull()
                .filter(Category::getIsActive)
                .map(categoryMapper::toDto);
    }

    public Flux<CategoryDto> findByParentId(UUID parentId) {
        return categoryRepository.findByParentId(parentId)
                .filter(Category::getIsActive)
                .map(categoryMapper::toDto);
    }

    public Mono<CategoryDto> findById(UUID id) {
        return categoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Category not found")))
                .map(categoryMapper::toDto);
    }

    public Mono<CategoryDto> findBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Category not found")))
                .map(categoryMapper::toDto);
    }

    public Mono<CategoryDto> create(CategoryDto dto) {
        return categoryRepository.existsBySlug(dto.getSlug())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new BadRequestException("Category slug already exists"));
                    }
                    Category category = categoryMapper.toEntity(dto);
                    category.setId(UUID.randomUUID());
                    category.setIsActive(true);
                    return categoryRepository.save(category);
                })
                .map(categoryMapper::toDto);
    }

    public Mono<CategoryDto> update(UUID id, CategoryDto dto) {
        return categoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Category not found")))
                .flatMap(existing -> {
                    existing.setName(dto.getName());
                    existing.setDescription(dto.getDescription());
                    existing.setImageUrl(dto.getImageUrl());
                    if (dto.getParentId() != null) {
                        existing.setParentId(dto.getParentId());
                    }
                    return categoryRepository.save(existing);
                })
                .map(categoryMapper::toDto);
    }

    public Mono<Void> delete(UUID id) {
        return categoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Category not found")))
                .flatMap(category -> {
                    category.setIsActive(false);
                    return categoryRepository.save(category);
                })
                .then();
    }
}
