package com.ecommerce.admin.controller;

import com.ecommerce.dto.OrderCreateRequest;
import com.ecommerce.dto.OrderDto;
import com.ecommerce.dto.PagedResponse;
import com.ecommerce.model.Order;
import com.ecommerce.service.OrderService;
import com.ecommerce.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Admin order surface. Authorization is enforced by path matching in
 * {@link com.ecommerce.security.AccessRules} (/api/admin/** -> hasRole(ADMIN)),
 * so this controller contains no role checks. Operations:
 *
 *   GET    /            list (paged, filter by status)
 *   GET    /{id}        detail
 *   POST   /            create on behalf of a customer
 *   PATCH  /{id}/status advance status (state-machine validated)
 *   POST   /{id}/cancel cancel + restock (or release reservation)
 *   POST   /{id}/mark-paid mark paid offline / for dev testing without Stripe
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@Tag(name = "Admin - Orders", description = "Order management (admin)")
public class AdminOrderController {

    private final OrderService orderService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "List orders (paged, optional status filter)")
    public Mono<PagedResponse<OrderDto>> list(
            @RequestParam(required = false) Order.OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orderService.findAllForAdmin(status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    public Mono<OrderDto> get(@PathVariable UUID id) {
        return orderService.findByIdForAdmin(id);
    }

    @PostMapping
    @Operation(summary = "Create an order on behalf of a customer")
    public Mono<OrderDto> create(@RequestParam UUID customerId,
                                 @Valid @RequestBody OrderCreateRequest request) {
        return currentAdminId().flatMap(adminId ->
                orderService.createForCustomer(adminId, customerId, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Transition order status (state-machine validated)")
    public Mono<OrderDto> transition(@PathVariable UUID id,
                                      @RequestBody Map<String, String> body) {
        Order.OrderStatus next = Order.OrderStatus.valueOf(body.get("status"));
        return orderService.transitionStatus(id, next);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel order + restock (or release reservation if PENDING)")
    public Mono<OrderDto> cancel(@PathVariable UUID id,
                                  @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return orderService.cancelByAdmin(id, reason);
    }

    @PostMapping("/{id}/mark-paid")
    @Operation(summary = "Manually mark an order as paid (offline payment / dev testing)")
    public Mono<OrderDto> markPaid(@PathVariable UUID id,
                                    @RequestBody(required = false) Map<String, String> body) {
        String paymentRef = body != null ? body.getOrDefault("reference", "manual:admin") : "manual:admin";
        return orderService.markPaid(id, paymentRef)
                .flatMap(o -> orderService.findByIdForAdmin(o.getId()));
    }

    private Mono<UUID> currentAdminId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .flatMap(userService::findByEmail)
                .map(u -> u.getId());
    }
}
