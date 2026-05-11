package com.ecommerce.admin.controller;

import com.ecommerce.dto.UserDto;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Admin-only customer search. Backs the customer-picker in the admin
 * "create order on behalf of customer" wizard.
 */
@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
@Tag(name = "Admin - Customers", description = "Customer search (admin)")
public class AdminCustomerController {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @GetMapping
    @Operation(summary = "Search active customers by email or name (max 20 results)")
    public Flux<UserDto> search(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) {
            return Flux.empty();
        }
        return userRepository.searchCustomers(q.trim()).map(userMapper::toDto);
    }
}
