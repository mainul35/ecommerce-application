package com.ecommerce.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for HTTP authorization rules.
 *
 * Permission changes (role names, public/protected paths, granular admin/manager
 * splits, adding new role-gated areas) are made here only - never in controllers
 * or services. Controllers and services stay focused on business logic; this
 * class owns "who can call what".
 *
 * Role model:
 *   CUSTOMER   - storefront shoppers, customer checkout / coupons
 *   MANAGER    - limited staff: products + categories only
 *   ADMIN      - full admin: products, categories, discounts, coupons,
 *                templates, orders, managers, settings
 *
 * Conventions:
 *   /api/auth/**                         public (customer login)
 *   /api/admin/auth/**                   public (admin/manager login)
 *   GET /api/products/**, /categories    public (catalog browsing)
 *   /api/webhooks/stripe (POST)          public, signature-verified inside the handler
 *   /api/admin/managers/**               ADMIN only - managing the staff list
 *   /api/admin/products/**               ADMIN or MANAGER - the limited-staff surface
 *   /api/admin/categories/**             ADMIN or MANAGER
 *   /api/admin/me/**                     ADMIN or MANAGER - self-profile
 *   /api/admin/**                        ADMIN only - everything else (discounts,
 *                                        coupons, templates, orders, customers)
 *   /api/orders/**                       CUSTOMER
 *   /api/coupons/**                      CUSTOMER (cart preview of a code)
 *   everything else                      authenticated
 *
 * Order matters: Spring evaluates pathMatchers top-to-bottom, first match wins,
 * so the more-specific staff-shared paths sit ABOVE the catch-all admin rule.
 */
@Component
public class AccessRules {

    public AuthorizeExchangeSpec apply(AuthorizeExchangeSpec exchanges) {
        return exchanges
                .pathMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**").permitAll()
                .pathMatchers("/api/auth/**").permitAll()
                // Admin/manager login surface: public, but UserService.adminLogin rejects
                // everyone except ADMIN and MANAGER. MUST come before /api/admin/** below.
                .pathMatchers("/api/admin/auth/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                // Stripe webhook is public but signature-verified inside the handler.
                .pathMatchers(HttpMethod.POST, "/api/webhooks/stripe").permitAll()
                // ADMIN-only sub-surfaces (must come BEFORE the staff-shared rules so
                // they take precedence for these specific paths).
                .pathMatchers("/api/admin/managers/**").hasRole("ADMIN")
                // Staff-shared surfaces: ADMIN or MANAGER.
                .pathMatchers("/api/admin/products/**").hasAnyRole("ADMIN", "MANAGER")
                .pathMatchers("/api/admin/categories/**").hasAnyRole("ADMIN", "MANAGER")
                .pathMatchers("/api/admin/me/**").hasAnyRole("ADMIN", "MANAGER")
                // Catch-all for everything else under /api/admin (discounts, coupons,
                // templates, orders, customers, discount-templates): ADMIN only.
                .pathMatchers("/api/admin/**").hasRole("ADMIN")
                // Customer self-checkout: only CUSTOMER role can place/cancel/list their own orders.
                .pathMatchers("/api/orders/**").hasRole("CUSTOMER")
                .pathMatchers("/api/coupons/**").hasRole("CUSTOMER")
                .anyExchange().authenticated();
    }
}
