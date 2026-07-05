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
                // Email confirmation is opened from the inbox link, possibly with no
                // session - the opaque token authenticates it. Other verification
                // endpoints (status, resend, phone) require the authed account.
                .pathMatchers(HttpMethod.POST, "/api/verification/email/verify").permitAll()
                // Admin/manager login surface: public, but UserService.adminLogin rejects
                // everyone except ADMIN and MANAGER. MUST come before /api/admin/** below.
                .pathMatchers("/api/admin/auth/**").permitAll()
                // Dispute evidence is sensitive and party-scoped. It is NO LONGER
                // served statically - files live in a private dir and are streamed
                // only via the party-checked endpoint
                // GET /api/disputes/{id}/attachments/{id}/file (DisputeService
                // #attachmentForViewer). Deny any lingering legacy /uploads/disputes
                // path outright so nothing is ever served without a party check.
                // MUST come before the public /uploads/** rule below (first match wins).
                .pathMatchers("/uploads/disputes/**").denyAll()
                // Uploaded product photos/videos must be publicly readable for storefront display.
                .pathMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                // Currencies and regions: storefront needs them anonymously to render
                // prices and to map detected country -> region/currency.
                .pathMatchers(HttpMethod.GET, "/api/currencies/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/regions/**").permitAll()
                // Stripe webhook is public but signature-verified inside the handler.
                .pathMatchers(HttpMethod.POST, "/api/webhooks/stripe").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/payment-gateways").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/search").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/search/reindex").hasRole("ADMIN")
                .pathMatchers("/api/payment/mock/**").permitAll()
                // Payment gateway callbacks — must be public so remote gateway servers
                // and customer browsers can reach them without a session token.
                .pathMatchers("/api/payment/sslcommerz/**").permitAll()
                .pathMatchers("/api/payment/paypay/**").permitAll()
                .pathMatchers("/api/payment/linepay/**").permitAll()
                .pathMatchers("/api/webhooks/omise").permitAll()
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
                // Orders and coupons: any authenticated user (CUSTOMER, ADMIN, MANAGER) can
                // place orders through the storefront — the admin panel and storefront share
                // the same localStorage token, so restricting to CUSTOMER-only blocks admins
                // who test the storefront while logged into the admin panel.
                // OrderService scopes each order to the authenticated user's account.
                .pathMatchers("/api/orders/**").authenticated()
                .pathMatchers("/api/coupons/**").authenticated()
                // Buyer protection surfaces: disputes (buyer/seller party-checked in
                // DisputeService), wallets, and return requests are per-user scoped
                // inside the services - any authenticated account may reach them.
                .pathMatchers("/api/disputes/**").authenticated()
                .pathMatchers("/api/wallet/**").authenticated()
                .pathMatchers("/api/returns/**").authenticated()
                // Seller e-KYC: per-user scoped inside KycService (the evidence
                // file endpoint additionally party-checks owner-or-staff).
                .pathMatchers("/api/kyc/**").authenticated()
                .pathMatchers("/api/verification/**").authenticated()
                .anyExchange().authenticated();
    }
}
