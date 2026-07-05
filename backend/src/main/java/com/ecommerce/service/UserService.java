package com.ecommerce.service;

import com.ecommerce.model.User;
import com.ecommerce.dto.AdminProfileUpdateRequest;
import com.ecommerce.dto.ManagerCreateRequest;
import com.ecommerce.dto.UserDto;
import com.ecommerce.dto.auth.AuthResponse;
import com.ecommerce.dto.auth.LoginRequest;
import com.ecommerce.dto.auth.RegisterRequest;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.exception.UnauthorizedException;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String USER_NOT_FOUND = "User not found";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final VerificationService verificationService;

    public Mono<AuthResponse> register(RegisterRequest request) {
        return userRepository.existsByEmail(request.getEmail())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new BadRequestException("Email already registered"));
                    }
                    User user = userMapper.toEntity(request);
                    user.setId(UUID.randomUUID());
                    user.setPassword(passwordEncoder.encode(request.getPassword()));
                    return userRepository.save(user)
                            // Fire the email verification link immediately on signup.
                            // Best-effort: a mail failure must not fail registration.
                            .flatMap(saved -> verificationService.sendEmailVerification(saved)
                                    .onErrorResume(e -> Mono.empty())
                                    .thenReturn(saved));
                })
                // Registration is a storefront action - mint storefront-scoped tokens.
                .map(user -> buildAuthResponse(user, JwtTokenProvider.Audience.STOREFRONT));
    }

    /**
     * Customer-side login. Accepts CUSTOMER and VENDOR accounts - sellers
     * are storefront users too. Rejects ADMIN/MANAGER with a generic
     * {@code Invalid credentials} message - staff MUST use the admin login flow.
     * Issues STOREFRONT-audience tokens.
     */
    public Mono<AuthResponse> login(LoginRequest request) {
        return authenticate(request, JwtTokenProvider.Audience.STOREFRONT,
                role -> role == User.UserRole.CUSTOMER || role == User.UserRole.VENDOR);
    }

    /**
     * Admin-console login. Accepts both ADMIN and MANAGER accounts - they
     * share the same login surface but see different sidebars / route
     * permissions once inside. Rejects everyone else with the same generic
     * message (no information leak about whether the account exists or
     * is a customer). Issues ADMIN-audience tokens, which are the only tokens
     * the admin API will accept.
     */
    public Mono<AuthResponse> adminLogin(LoginRequest request) {
        return authenticate(request, JwtTokenProvider.Audience.ADMIN,
                role -> role == User.UserRole.ADMIN || role == User.UserRole.MANAGER);
    }

    /** Exchange a storefront refresh token for a fresh storefront token pair. */
    public Mono<AuthResponse> refreshStorefront(String refreshToken) {
        return refresh(refreshToken, JwtTokenProvider.Audience.STOREFRONT,
                role -> role == User.UserRole.CUSTOMER || role == User.UserRole.VENDOR);
    }

    /** Exchange an admin refresh token for a fresh admin token pair. */
    public Mono<AuthResponse> refreshAdmin(String refreshToken) {
        return refresh(refreshToken, JwtTokenProvider.Audience.ADMIN,
                role -> role == User.UserRole.ADMIN || role == User.UserRole.MANAGER);
    }

    private Mono<AuthResponse> authenticate(LoginRequest request, JwtTokenProvider.Audience audience,
                                            java.util.function.Predicate<User.UserRole> roleAllowed) {
        return userRepository.findByEmail(request.getEmail())
                .switchIfEmpty(Mono.error(new UnauthorizedException("Invalid credentials")))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())
                            || !roleAllowed.test(user.getRole())) {
                        return Mono.error(new UnauthorizedException("Invalid credentials"));
                    }
                    if (!user.getIsActive()) {
                        return Mono.error(new UnauthorizedException("Account is disabled"));
                    }
                    return Mono.just(buildAuthResponse(user, audience));
                });
    }

    /**
     * Validate a refresh token for the given surface and mint a fresh token pair
     * (rotation). Rejects anything that is not a valid, matching-audience refresh
     * token, or whose account is no longer allowed on that surface / is disabled.
     */
    private Mono<AuthResponse> refresh(String refreshToken, JwtTokenProvider.Audience audience,
                                       java.util.function.Predicate<User.UserRole> roleAllowed) {
        if (refreshToken == null
                || !jwtTokenProvider.validateToken(refreshToken)
                || !jwtTokenProvider.isRefreshToken(refreshToken)
                || jwtTokenProvider.getAudience(refreshToken) != audience) {
            return Mono.error(new UnauthorizedException("Invalid refresh token"));
        }
        String email = jwtTokenProvider.getEmailFromToken(refreshToken);
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new UnauthorizedException("Invalid refresh token")))
                .flatMap(user -> {
                    if (!roleAllowed.test(user.getRole()) || !user.getIsActive()) {
                        return Mono.error(new UnauthorizedException("Invalid refresh token"));
                    }
                    return Mono.just(buildAuthResponse(user, audience));
                });
    }

    private AuthResponse buildAuthResponse(User user, JwtTokenProvider.Audience audience) {
        return AuthResponse.builder()
                .user(userMapper.toDto(user))
                .token(jwtTokenProvider.generateAccessToken(user, audience))
                .refreshToken(jwtTokenProvider.generateRefreshToken(user, audience))
                .build();
    }

    public Mono<UserDto> findById(UUID id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(USER_NOT_FOUND)))
                .map(userMapper::toDto);
    }

    public Mono<UserDto> findByEmail(String email) {
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(USER_NOT_FOUND)))
                .map(userMapper::toDto);
    }

    public Mono<User> findUserEntityByEmail(String email) {
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(USER_NOT_FOUND)));
    }

    // ---------- Manager (limited-admin) management ----------

    public Flux<UserDto> listManagers() {
        return userRepository.findAllManagers().map(userMapper::toDto);
    }

    /**
     * Admin provisions a new MANAGER account. The email/username must be unique
     * across all users. The manager can sign in immediately at /admin/login.
     */
    public Mono<UserDto> createManager(ManagerCreateRequest request) {
        return userRepository.existsByEmail(request.getEmail())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new BadRequestException("Email or username already in use"));
                    }
                    User manager = User.builder()
                            .email(request.getEmail())
                            .password(passwordEncoder.encode(request.getPassword()))
                            .firstName(request.getFirstName())
                            .lastName(request.getLastName())
                            .role(User.UserRole.MANAGER)
                            .isActive(true)
                            .emailVerified(true)
                            .build();
                    manager.setId(UUID.randomUUID());
                    return userRepository.save(manager);
                })
                .map(userMapper::toDto);
    }

    /**
     * Block / unblock a manager (toggles the user's isActive flag). Blocked
     * managers cannot log in to the admin console. Refuses to touch any
     * non-MANAGER account to prevent admins from accidentally locking
     * themselves or other admins out via this surface.
     */
    public Mono<UserDto> setManagerActive(UUID managerId, boolean active) {
        return userRepository.findById(managerId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(USER_NOT_FOUND)))
                .flatMap(user -> {
                    if (user.getRole() != User.UserRole.MANAGER) {
                        return Mono.error(new BadRequestException("Target user is not a manager"));
                    }
                    user.setIsActive(active);
                    return userRepository.save(user);
                })
                .map(userMapper::toDto);
    }

    /**
     * Update the profile of the user identified by {@code currentEmail}.
     * Email change is allowed if the new email is not taken. Password is
     * changed only when {@code newPassword} is provided, and {@code currentPassword}
     * must match.
     */
    public Mono<UserDto> updateProfile(String currentEmail, AdminProfileUpdateRequest request) {
        return userRepository.findByEmail(currentEmail)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(USER_NOT_FOUND)))
                .flatMap(user -> {
                    boolean emailChanged = !user.getEmail().equalsIgnoreCase(request.getEmail());
                    Mono<Boolean> emailAvailable = emailChanged
                            ? userRepository.existsByEmail(request.getEmail())
                                    .map(exists -> !exists)
                            : Mono.just(true);

                    return emailAvailable.flatMap(available -> {
                        if (Boolean.FALSE.equals(available)) {
                            return Mono.error(new BadRequestException("Email already in use"));
                        }
                        boolean wantsPasswordChange = request.getNewPassword() != null
                                && !request.getNewPassword().isBlank();
                        if (wantsPasswordChange) {
                            if (request.getCurrentPassword() == null
                                    || !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                                return Mono.error(new UnauthorizedException("Current password is incorrect"));
                            }
                            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
                        }
                        user.setEmail(request.getEmail());
                        user.setFirstName(request.getFirstName());
                        user.setLastName(request.getLastName());
                        return userRepository.save(user);
                    });
                })
                .map(userMapper::toDto);
    }
}
