package com.ecommerce.mapper;

import com.ecommerce.model.User;
import com.ecommerce.dto.UserDto;
import com.ecommerce.dto.auth.RegisterRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto toDto(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "role", expression = "java(com.ecommerce.model.User.UserRole.CUSTOMER)")
    @Mapping(target = "isActive", expression = "java(true)")
    @Mapping(target = "emailVerified", expression = "java(false)")
    User toEntity(RegisterRequest request);
}
