package com.ecommerce.security;

import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class ReactiveUserDetailsService implements org.springframework.security.core.userdetails.ReactiveUserDetailsService {

    private final UserRepository userRepository;

    @Override
    public Mono<UserDetails> findByUsername(String email) {
        return userRepository.findByEmail(email)
                .map(user -> User.builder()
                        .username(user.getEmail())
                        .password(user.getPassword())
                        .authorities(Collections.singletonList(
                                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                        .accountExpired(false)
                        .accountLocked(!user.getIsActive())
                        .credentialsExpired(false)
                        .disabled(!user.getIsActive())
                        .build());
    }
}
