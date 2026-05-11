package com.ecommerce.repository;

import com.ecommerce.model.User;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface UserRepository extends R2dbcRepository<User, UUID> {

    Mono<User> findByEmail(String email);

    Mono<Boolean> existsByEmail(String email);

    @Query("SELECT * FROM users " +
           "WHERE role = 'CUSTOMER' AND is_active = true AND (" +
           "  LOWER(email) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "  LOWER(first_name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "  LOWER(last_name)  LIKE LOWER(CONCAT('%', :q, '%'))" +
           ") ORDER BY email LIMIT 20")
    Flux<User> searchCustomers(String q);

    @Query("SELECT * FROM users WHERE role = 'MANAGER' ORDER BY created_at DESC")
    Flux<User> findAllManagers();
}
