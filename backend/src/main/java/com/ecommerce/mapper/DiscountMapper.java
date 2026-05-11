package com.ecommerce.mapper;

import com.ecommerce.dto.DiscountDto;
import com.ecommerce.model.Discount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DiscountMapper {

    DiscountDto toDto(Discount discount);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Discount toEntity(DiscountDto dto);
}
