package com.ecommerce.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for HTTP authorization rules.
 *
 * Permission changes (role names, public/protected paths, granular admin/vendor splits,
 * adding new role-gated areas) are made here only - never in controllers or services.
 * Controllers and services stay focused on business logic; this class owns "who can call what".
 *
 * Conventions:
 *   /api/auth/**                       public
 *   GET /api/products/**, /categories  public  (catalog browsing)
 *   /api/admin/**                      ADMIN role required
 *   /api/orders/**                     authenticated (any logged-in user)
 *   everything else                    authenticated
 */
@Component
public class AccessRules {

    public AuthorizeExchangeSpec apply(AuthorizeExchangeSpec exchanges) {
        return exchanges
                .pathMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**").permitAll()
                .pathMatchers("/api/auth/**").permitAll()
                // Admin login surface: public, but UserService.adminLogin rejects non-ADMIN accounts.
                // MUST come before the /api/admin/** rule below, which would otherwise demand a JWT.
                .pathMatchers("/api/admin/auth/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                // Stripe webhook is public but signature-verified inside the handler.
                .pathMatchers(HttpMethod.POST, "/api/webhooks/stripe").permitAll()
                .pathMatchers("/api/admin/**").hasRole("ADMIN")
                // Customer self-checkout: only CUSTOMER role can place/cancel/list their own orders.
                // Admin orders go through /api/admin/orders/** instead.
                .pathMatchers("/api/orders/**").hasRole("CUSTOMER")
                // Coupon preview is a customer-only action (admins manage coupons via /api/admin/coupons).
                .pathMatchers("/api/coupons/**").hasRole("CUSTOMER")
                .anyExchange().authenticated();
    }
}
