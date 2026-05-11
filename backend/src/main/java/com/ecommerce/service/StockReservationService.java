package com.ecommerce.service;

import com.ecommerce.exception.InsufficientStockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Owns the atomic "promise N units of a product" operation. Available stock
 * is computed as {@code products.stock - SUM(active reservations)} and the
 * insert only succeeds when that value is >= the requested quantity - all
 * inside a single SQL statement so concurrent reservation attempts cannot
 * over-promise.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockReservationService {

    private static final String P_ORDER_ID = "orderId";

    private final DatabaseClient db;

    /**
     * Reserve {@code quantity} units of {@code productId} for {@code orderId} until {@code expiresAt}.
     * Errors with {@link InsufficientStockException} if insufficient stock is available.
     */
    public Mono<UUID> reserve(UUID orderId, UUID productId, int quantity, LocalDateTime expiresAt) {
        UUID reservationId = UUID.randomUUID();

        // Conditional INSERT: only emits a row if (stock - active reservations) >= quantity.
        // The subquery and the INSERT run as one statement, so contention between concurrent
        // reservations on the same product is resolved at the database level.
        String sql = """
                INSERT INTO stock_reservations (id, order_id, product_id, quantity, expires_at, created_at, updated_at)
                SELECT :id, :orderId, :productId, :quantity, :expiresAt, NOW(), NOW()
                WHERE (
                    SELECT p.stock - COALESCE((
                        SELECT SUM(sr.quantity)
                        FROM stock_reservations sr
                        WHERE sr.product_id = :productId AND sr.expires_at > NOW()
                    ), 0)
                    FROM products p WHERE p.id = :productId
                ) >= :quantity
                RETURNING id
                """;

        return db.sql(sql)
                .bind("id", reservationId)
                .bind(P_ORDER_ID, orderId)
                .bind("productId", productId)
                .bind("quantity", quantity)
                .bind("expiresAt", expiresAt)
                .fetch()
                .one()
                .map(row -> (UUID) row.get("id"))
                .switchIfEmpty(Mono.error(new InsufficientStockException(
                        "Insufficient stock for product " + productId + " (requested " + quantity + ")")));
    }

    /**
     * Convert all active reservations for an order into permanent stock decrements
     * and remove the reservation rows. Used at payment confirmation.
     */
    public Mono<Long> commit(UUID orderId) {
        // 1) Decrement products.stock by the reserved quantities
        // 2) Delete the reservation rows
        // The CHECK (stock >= 0) guarantees we never over-decrement.
        String decrementSql = """
                UPDATE products p
                SET stock = stock - sr.quantity
                FROM stock_reservations sr
                WHERE sr.product_id = p.id AND sr.order_id = :orderId
                """;
        String deleteSql = "DELETE FROM stock_reservations WHERE order_id = :orderId";

        return db.sql(decrementSql).bind(P_ORDER_ID, orderId).fetch().rowsUpdated()
                .then(db.sql(deleteSql).bind(P_ORDER_ID, orderId).fetch().rowsUpdated());
    }

    /**
     * Drop reservations without touching products.stock. Used on cancel/expire of an
     * unpaid order.
     */
    public Mono<Long> release(UUID orderId) {
        return db.sql("DELETE FROM stock_reservations WHERE order_id = :orderId")
                .bind(P_ORDER_ID, orderId)
                .fetch()
                .rowsUpdated();
    }

    /**
     * Increment products.stock by the order's line-item quantities. Used when
     * cancelling a CONFIRMED/PROCESSING order whose stock was already committed
     * via {@link #commit(UUID)} - this puts the units back on the shelf.
     */
    public Mono<Long> restore(UUID orderId) {
        String sql = """
                UPDATE products p
                SET stock = stock + oi.quantity
                FROM order_items oi
                WHERE oi.product_id = p.id AND oi.order_id = :orderId
                """;
        return db.sql(sql).bind(P_ORDER_ID, orderId).fetch().rowsUpdated();
    }
}
