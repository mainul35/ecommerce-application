package com.ecommerce.mapper;

import com.ecommerce.dto.DiscountTemplateDto;
import com.ecommerce.model.DiscountTemplate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DiscountTemplateMapper {

    DiscountTemplateDto toDto(DiscountTemplate entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    DiscountTemplate toEntity(DiscountTemplateDto dto);
}
