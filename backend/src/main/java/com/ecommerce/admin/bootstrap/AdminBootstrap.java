package com.ecommerce.admin.bootstrap;

import com.ecommerce.model.User;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Seeds a default ADMIN user on first startup. Idempotent: skips if the
 * configured admin email already exists.
 *
 * Credentials are read from configuration so production deployments can
 * override via env vars (ADMIN_BOOTSTRAP_EMAIL / ADMIN_BOOTSTRAP_PASSWORD).
 *
 * subscribe() is used here because this is application-bootstrap code, not
 * request-handling code. There is no upstream reactor pipeline to compose
 * with; the chain must be terminated to fire.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.bootstrap.email:admin}")
    private String adminEmail;

    @Value("${admin.bootstrap.password:secret}")
    private String adminPassword;

    @Value("${admin.bootstrap.first-name:System}")
    private String adminFirstName;

    @Value("${admin.bootstrap.last-name:Administrator}")
    private String adminLastName;

    @Override
    public void run(ApplicationArguments args) {
        userRepository.existsByEmail(adminEmail)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.debug("Admin bootstrap: user '{}' already exists, skipped", adminEmail);
                        return Mono.<User>empty();
                    }
                    User admin = User.builder()
                            .email(adminEmail)
                            .password(passwordEncoder.encode(adminPassword))
                            .firstName(adminFirstName)
                            .lastName(adminLastName)
                            .role(User.UserRole.ADMIN)
                            .isActive(true)
                            .emailVerified(true)
                            .build();
                    admin.setId(UUID.randomUUID());
                    return userRepository.save(admin);
                })
                .subscribe(
                        saved -> log.info("Seeded admin user: {}", saved.getEmail()),
                        err -> log.error("Admin bootstrap failed", err));
    }
}
