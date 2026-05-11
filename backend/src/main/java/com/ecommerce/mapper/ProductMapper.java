package com.ecommerce.mapper;

import com.ecommerce.model.Product;
import com.ecommerce.dto.CategoryDto;
import com.ecommerce.dto.ProductCreateRequest;
import com.ecommerce.dto.ProductDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public abstract class ProductMapper {

    @Autowired
    protected ObjectMapper objectMapper;

    @Mapping(target = "category", ignore = true)
    @Mapping(target = "images", source = "images", qualifiedByName = "jsonStringToStringList")
    @Mapping(target = "attributes", source = "attributes", qualifiedByName = "jsonStringToMap")
    @Mapping(target = "discountedPrice", ignore = true)
    @Mapping(target = "discountPercent", ignore = true)
    @Mapping(target = "discountName", ignore = true)
    @Mapping(target = "discountEndsAt", ignore = true)
    public abstract ProductDto toDto(Product product);

    public ProductDto toDto(Product product, CategoryDto category) {
        ProductDto dto = toDto(product);
        dto.setCategory(category);
        return dto;
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isActive", constant = "true")
    @Mapping(target = "vendorId", ignore = true)
    @Mapping(target = "images", source = "images", qualifiedByName = "stringListToJsonString")
    @Mapping(target = "attributes", source = "attributes", qualifiedByName = "mapToJsonString")
    public abstract Product toEntity(ProductCreateRequest request);

    @Named("jsonStringToStringList")
    protected List<String> jsonStringToStringList(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    @Named("stringListToJsonString")
    protected String stringListToJsonString(List<String> list) {
        if (list == null) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @Named("jsonStringToMap")
    protected Map<String, Object> jsonStringToMap(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    @Named("mapToJsonString")
    protected String mapToJsonString(Map<String, Object> map) {
        if (map == null) return "{}";
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
