package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A promise of {@link #quantity} units of {@link #productId} held for an
 * order until {@link #expiresAt}. Available stock for any product is
 * {@code products.stock − SUM(active reservations)}.
 *
 * Reservations are cleared in three ways:
 *   - paid: reservation deleted, products.stock decremented permanently
 *   - cancelled: reservation deleted, products.stock unchanged
 *   - expired: scheduled job deletes them and cancels the parent order
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("stock_reservations")
public class StockReservation extends BaseEntity {

    @Column("order_id")
    private UUID orderId;

    @Column("product_id")
    private UUID productId;

    @Column("quantity")
    private Integer quantity;

    @Column("expires_at")
    private LocalDateTime expiresAt;
}
