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
 * SECURITY: the built-in defaults ({@code admin}/{@code secret}) exist only so a
 * fresh dev machine has an admin to log in with. They are guessable and MUST be
 * overridden in any shared environment (see docs/POST_DEPLOYMENT_SECRETS.md).
 * Two guards enforce this without breaking local dev:
 *   - set {@code admin.bootstrap.enabled=false} in production to disable the
 *     fallback seed entirely (rely on the vault-provisioned / rotated admin);
 *   - if the default password is still in use, a prominent WARN is logged on
 *     every boot so a misconfiguration cannot pass unnoticed.
 *
 * subscribe() is used here because this is application-bootstrap code, not
 * request-handling code. There is no upstream reactor pipeline to compose
 * with; the chain must be terminated to fire.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    /** The insecure built-in default - flagged loudly whenever it is in effect. */
    private static final String DEFAULT_PASSWORD = "secret";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.bootstrap.enabled:true}")
    private boolean bootstrapEnabled;

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
        if (!bootstrapEnabled) {
            log.info("Admin bootstrap disabled (admin.bootstrap.enabled=false) - no fallback admin seeded");
            return;
        }
        if (DEFAULT_PASSWORD.equals(adminPassword)) {
            log.warn("SECURITY: admin bootstrap is using the default password '{}'. Set "
                    + "ADMIN_BOOTSTRAP_PASSWORD to a strong value (or admin.bootstrap.enabled=false) "
                    + "before exposing this deployment. See docs/POST_DEPLOYMENT_SECRETS.md", DEFAULT_PASSWORD);
        }
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
