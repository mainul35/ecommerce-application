package com.ecommerce.security;

import com.ecommerce.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * Issues and validates JWTs. Hardened token model:
 *
 * <ul>
 *   <li><b>Audience ({@code aud})</b> - every token is minted for exactly one
 *       surface: {@link Audience#STOREFRONT} or {@link Audience#ADMIN}. The
 *       admin API only accepts ADMIN-audience tokens (enforced in
 *       {@link JwtAuthenticationFilter}), so a stolen storefront token - the
 *       storefront being the largest XSS surface - cannot reach admin APIs even
 *       if the account happens to hold an ADMIN/MANAGER role.</li>
 *   <li><b>Type</b> - short-lived <i>access</i> tokens authenticate API calls;
 *       longer-lived <i>refresh</i> tokens are accepted ONLY at the refresh
 *       endpoints to mint new access tokens. A refresh token can never
 *       authenticate a normal request.</li>
 *   <li><b>jti</b> - a unique id per token, enabling a future shared-store
 *       denylist for true single-token revocation (see POST_DEPLOYMENT_SECRETS).</li>
 * </ul>
 *
 * Authorization at request time still relies on the DB-loaded role (see
 * {@link ReactiveUserDetailsService}), so the {@code role} claim is informational
 * and a tampered claim body is ignored.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** The single surface a token is scoped to. */
    public enum Audience {
        STOREFRONT("storefront"),
        ADMIN("admin");

        private final String value;

        Audience(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static Audience fromValue(String value) {
            for (Audience a : values()) {
                if (a.value.equals(value)) {
                    return a;
                }
            }
            return null;
        }
    }

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_USER_ID = "userId";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long jwtRefreshExpiration;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user, Audience audience) {
        return build(user, audience, TYPE_ACCESS, jwtExpiration);
    }

    public String generateRefreshToken(User user, Audience audience) {
        return build(user, audience, TYPE_REFRESH, jwtRefreshExpiration);
    }

    private String build(User user, Audience audience, String type, long ttlMillis) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + ttlMillis);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())            // jti
                .subject(user.getEmail())
                .audience().add(audience.value()).and()
                .claim(CLAIM_USER_ID, user.getId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (SignatureException ex) {
            log.error("Invalid JWT signature");
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty");
        }
        return false;
    }

    /** True only for access tokens - the only type that may authenticate a request. */
    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(safeClaim(token, CLAIM_TYPE));
    }

    /** True only for refresh tokens - accepted solely at the refresh endpoints. */
    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(safeClaim(token, CLAIM_TYPE));
    }

    /** The surface a token is scoped to, or {@code null} if absent/invalid. */
    public Audience getAudience(String token) {
        try {
            Set<String> audiences = parseClaims(token).getAudience();
            if (audiences == null || audiences.isEmpty()) {
                return null;
            }
            return Audience.fromValue(audiences.iterator().next());
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String safeClaim(String token, String name) {
        try {
            return parseClaims(token).get(name, String.class);
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
