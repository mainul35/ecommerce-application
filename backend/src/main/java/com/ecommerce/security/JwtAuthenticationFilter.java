package com.ecommerce.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final ReactiveUserDetailsService userDetailsService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = extractToken(exchange);

        if (!StringUtils.hasText(token) || !jwtTokenProvider.validateToken(token)) {
            return chain.filter(exchange);
        }

        // Only ACCESS tokens authenticate API calls. A refresh token presented as
        // a bearer credential is ignored (it is accepted solely at the refresh
        // endpoints, which mint fresh access tokens).
        if (!jwtTokenProvider.isAccessToken(token)) {
            return chain.filter(exchange);
        }

        // Audience isolation: a storefront-scoped token must never authenticate an
        // admin API call, even for an ADMIN/MANAGER account. This caps the blast
        // radius of a stolen storefront token (the largest XSS surface) to the
        // storefront API. Admin-scoped tokens remain usable on any surface.
        String path = exchange.getRequest().getPath().value();
        if (isAdminApi(path)
                && jwtTokenProvider.getAudience(token) != JwtTokenProvider.Audience.ADMIN) {
            // Leave the request unauthenticated - AccessRules then yields 401/403.
            return chain.filter(exchange);
        }

        String email = jwtTokenProvider.getEmailFromToken(token);
        return userDetailsService.findByUsername(email)
                .map(userDetails -> new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()))
                .flatMap(authentication -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)));
    }

    /** Admin API surface, excluding the public admin auth endpoints. */
    private boolean isAdminApi(String path) {
        return path.startsWith("/api/admin/") && !path.startsWith("/api/admin/auth/");
    }

    private String extractToken(ServerWebExchange exchange) {
        String bearerToken = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
