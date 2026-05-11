package com.ecommerce.mapper;

import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.dto.AddressDto;
import com.ecommerce.dto.OrderDto;
import com.ecommerce.dto.OrderItemDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class OrderMapper {

    @Autowired
    protected ObjectMapper objectMapper;

    @Mapping(target = "items", ignore = true)
    @Mapping(target = "shippingAddress", source = "shippingAddress", qualifiedByName = "jsonStringToAddress")
    @Mapping(target = "billingAddress", source = "billingAddress", qualifiedByName = "jsonStringToAddress")
    public abstract OrderDto toDto(Order order);

    public OrderDto toDto(Order order, List<OrderItemDto> items) {
        OrderDto dto = toDto(order);
        dto.setItems(items);
        return dto;
    }

    public abstract OrderItemDto toItemDto(OrderItem item);

    @Named("jsonStringToAddress")
    protected AddressDto jsonStringToAddress(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, AddressDto.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
