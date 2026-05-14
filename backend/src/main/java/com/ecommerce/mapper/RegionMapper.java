package com.ecommerce.mapper;

import com.ecommerce.dto.RegionDto;
import com.ecommerce.model.Region;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RegionMapper {

    RegionDto toDto(Region region);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Region toEntity(RegionDto dto);
}
