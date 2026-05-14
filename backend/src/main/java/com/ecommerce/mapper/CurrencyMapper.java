package com.ecommerce.mapper;

import com.ecommerce.dto.CurrencyDto;
import com.ecommerce.model.Currency;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CurrencyMapper {

    CurrencyDto toDto(Currency currency);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Currency toEntity(CurrencyDto dto);
}
