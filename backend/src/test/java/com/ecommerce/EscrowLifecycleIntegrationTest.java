package com.ecommerce;

import com.ecommerce.dto.AddressDto;
import com.ecommerce.dto.DisputeDto;
import com.ecommerce.dto.DisputeResolveRequest;
import com.ecommerce.dto.OrderCreateRequest;
import com.ecommerce.dto.OrderDto;
import com.ecommerce.dto.OrderItemRequest;
import com.ecommerce.dto.ReturnRequestDto;
import com.ecommerce.model.Category;
import com.ecommerce.model.Dispute;
import com.ecommerce.model.EscrowTransaction;
import com.ecommerce.model.Order;
import com.ecommerce.model.Product;
import com.ecommerce.model.ReturnRequest;
import com.ecommerce.model.User;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.EscrowTransactionRepository;
import com.ecommerce.repository.OrderItemRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.DisputeService;
import com.ecommerce.service.EscrowService;
import com.ecommerce.service.OrderService;
import com.ecommerce.service.ReturnService;
import com.ecommerce.service.WalletService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end escrow lifecycle against a REAL PostgreSQL database (project
 * testing policy: integration tests must not mock the database). Flyway runs
 * all migrations on startup, so this also validates V16 on a fresh schema.
 *
 * Requires a dedicated Postgres on port {@code IT_DB_PORT} (default 5433):
 *
 *   docker run -d --name ecommerce-it-db -e POSTGRES_DB=ecommerce \
 *     -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
 *     -p 5433:5432 postgres:16-alpine
 *
 * Scenario: one checkout with items from two different sellers, paid by an
 * offline method (no refund-capable gateway -> refunds go to the wallet):
 *   1. markPaid splits the total into two HELD escrow groups
 *   2. DELIVERED starts the protection window
 *   3. seller A's group: dispute -> conversation -> escalate -> staff partial
 *      refund (buyer wallet) -> remainder released to seller A's wallet
 *   4. seller B's group: partial item return approved -> buyer wallet refund
 *   5. buyer confirms receipt -> seller B's remainder released
 *   6. order payment status ends PARTIALLY_REFUNDED; ledgers add up
 */
@SpringBootTest(properties = {
        "spring.r2dbc.url=r2dbc:postgresql://localhost:${IT_DB_PORT:5433}/ecommerce",
        "spring.flyway.url=jdbc:postgresql://localhost:${IT_DB_PORT:5433}/ecommerce",
        // Keep background jobs quiet during the test run.
        "escrow.release-initial-delay-ms=3600000",
        "reservation.cleanup-initial-delay-ms=3600000"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EscrowLifecycleIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private EscrowService escrowService;
    @Autowired private DisputeService disputeService;
    @Autowired private ReturnService returnService;
    @Autowired private WalletService walletService;

    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private EscrowTransactionRepository escrowRepository;

    @Test
    void fullEscrowLifecycle_twoSellers_disputeAndReturn() {
        String run = UUID.randomUUID().toString().substring(0, 8);

        // ---- Fixtures: buyer, staff, two sellers, two products ----
        User buyer = saveUser(run + "-buyer@test.local", User.UserRole.CUSTOMER);
        User staff = saveUser(run + "-admin@test.local", User.UserRole.ADMIN);
        User sellerA = saveUser(run + "-sellerA@test.local", User.UserRole.VENDOR);
        User sellerB = saveUser(run + "-sellerB@test.local", User.UserRole.VENDOR);

        Category category = categoryRepository.save(Category.builder()
                .id(UUID.randomUUID())
                .name("IT Cat " + run)
                .slug("it-cat-" + run)
                .isActive(true)
                .build()).block();
        assertThat(category).isNotNull();

        Product productA = saveProduct(run + "-A", new BigDecimal("40.00"), category.getId(), sellerA.getId());
        Product productB = saveProduct(run + "-B", new BigDecimal("25.00"), category.getId(), sellerB.getId());

        // ---- Checkout: 2x A ($80) + 2x B ($50) = $130, paid by cash ----
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(productA.getId(), 2),
                        new OrderItemRequest(productB.getId(), 2)),
                address(), address(), "cash", null);

        OrderDto order = orderService.create(buyer.getId(), request).block();
        assertThat(order).isNotNull();
        assertThat(order.getTotalAmount()).isEqualByComparingTo("130.00");
        UUID orderId = order.getId();

        // ---- Payment captured -> two HELD escrow groups summing to the total ----
        StepVerifier.create(orderService.markPaid(orderId, "test-pay-" + run))
                .assertNext(paid -> {
                    assertThat(paid.getPaymentStatus()).isEqualTo(Order.PaymentStatus.COMPLETED);
                    assertThat(paid.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
                })
                .verifyComplete();

        List<EscrowTransaction> held = escrowRepository.findByOrderId(orderId).collectList().block();
        assertThat(held).hasSize(2);
        assertThat(held).allMatch(tx -> tx.getStatus() == EscrowTransaction.EscrowStatus.HELD);
        assertThat(held.stream().map(EscrowTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("130.00");
        EscrowTransaction escrowA = held.stream()
                .filter(tx -> sellerA.getId().equals(tx.getSellerId())).findFirst().orElseThrow();
        EscrowTransaction escrowB = held.stream()
                .filter(tx -> sellerB.getId().equals(tx.getSellerId())).findFirst().orElseThrow();
        assertThat(escrowA.getAmount()).isEqualByComparingTo("80.00");
        assertThat(escrowB.getAmount()).isEqualByComparingTo("50.00");
        // Idempotency: webhook re-delivery must not duplicate escrow rows.
        orderService.markPaid(orderId, "test-pay-" + run).block();
        assertThat(escrowRepository.findByOrderId(orderId).count().block()).isEqualTo(2);

        // ---- Fulfilment to DELIVERED starts the protection window ----
        orderService.transitionStatus(orderId, Order.OrderStatus.PROCESSING).block();
        orderService.transitionStatus(orderId, Order.OrderStatus.SHIPPED).block();
        orderService.transitionStatus(orderId, Order.OrderStatus.DELIVERED).block();
        StepVerifier.create(escrowRepository.findById(escrowA.getId()))
                .assertNext(tx -> assertThat(tx.getHoldUntil()).isNotNull())
                .verifyComplete();

        // ---- Seller A's group: dispute -> message -> escalate -> partial refund ----
        DisputeDto dispute = disputeService
                .open(buyer.getId(), escrowA.getId(), null, "Item arrived damaged").block();
        assertThat(dispute).isNotNull();
        assertThat(dispute.getStatus()).isEqualTo(Dispute.DisputeStatus.OPEN);
        StepVerifier.create(escrowRepository.findById(escrowA.getId()))
                .assertNext(tx -> assertThat(tx.getStatus())
                        .isEqualTo(EscrowTransaction.EscrowStatus.DISPUTED))
                .verifyComplete();

        StepVerifier.create(disputeService.addMessage(
                        dispute.getId(), buyer.getId(), "See the crack on the left side", List.of()))
                .assertNext(msg -> assertThat(msg.getAuthorRole().name()).isEqualTo("BUYER"))
                .verifyComplete();
        StepVerifier.create(disputeService.addMessage(
                        dispute.getId(), sellerA.getId(), "Can you share a photo?", List.of()))
                .assertNext(msg -> assertThat(msg.getAuthorRole().name()).isEqualTo("SELLER"))
                .verifyComplete();

        disputeService.escalate(dispute.getId(), buyer.getId()).block();

        DisputeResolveRequest verdict = DisputeResolveRequest.builder()
                .action(DisputeResolveRequest.Action.REFUND)
                .refundAmount(new BigDecimal("5.00"))
                .note("Cosmetic damage - partial refund agreed")
                .build();
        StepVerifier.create(disputeService.resolve(dispute.getId(), staff.getId(), verdict))
                .assertNext(resolved -> {
                    assertThat(resolved.getStatus()).isEqualTo(Dispute.DisputeStatus.RESOLVED_REFUNDED);
                    assertThat(resolved.getRefundAmount()).isEqualByComparingTo("5.00");
                })
                .verifyComplete();

        // Partial dispute refund: $5 to the buyer's wallet (cash has no gateway),
        // the $75 remainder released to seller A's wallet, escrow concluded.
        StepVerifier.create(escrowRepository.findById(escrowA.getId()))
                .assertNext(tx -> {
                    assertThat(tx.getStatus()).isEqualTo(EscrowTransaction.EscrowStatus.RELEASED);
                    assertThat(tx.getRefundedAmount()).isEqualByComparingTo("5.00");
                })
                .verifyComplete();
        StepVerifier.create(walletService.getWallet(sellerA.getId()))
                .assertNext(w -> assertThat(w.getBalance()).isEqualByComparingTo("75.00"))
                .verifyComplete();

        // ---- Seller B's group: return 1 of 2 units of product B ----
        UUID itemBId = orderItemRepository.findByOrderId(orderId)
                .filter(i -> productB.getId().equals(i.getProductId()))
                .blockFirst().getId();

        ReturnRequestDto returnRequest = returnService
                .request(buyer.getId(), orderId, itemBId, 1, "Wrong size").block();
        assertThat(returnRequest).isNotNull();
        assertThat(returnRequest.getRefundAmount()).isEqualByComparingTo("25.00");

        StepVerifier.create(returnService.approve(staff.getId(), returnRequest.getId()))
                .assertNext(approved -> {
                    assertThat(approved.getStatus()).isEqualTo(ReturnRequest.ReturnStatus.REFUNDED);
                    assertThat(approved.getRefundDestination())
                            .isEqualTo(ReturnRequest.RefundDestination.WALLET);
                })
                .verifyComplete();
        StepVerifier.create(orderItemRepository.findById(itemBId))
                .assertNext(item -> assertThat(item.getReturnedQuantity()).isEqualTo(1))
                .verifyComplete();

        // ---- Buyer confirms receipt: seller B's $25 remainder is released ----
        escrowService.confirmReceipt(orderId, buyer.getId()).block();
        StepVerifier.create(escrowRepository.findById(escrowB.getId()))
                .assertNext(tx -> {
                    assertThat(tx.getStatus()).isEqualTo(EscrowTransaction.EscrowStatus.RELEASED);
                    assertThat(tx.getRefundedAmount()).isEqualByComparingTo("25.00");
                })
                .verifyComplete();
        StepVerifier.create(walletService.getWallet(sellerB.getId()))
                .assertNext(w -> assertThat(w.getBalance()).isEqualByComparingTo("25.00"))
                .verifyComplete();

        // ---- Final ledgers: buyer got $5 + $25 back; order is partially refunded ----
        StepVerifier.create(walletService.getWallet(buyer.getId()))
                .assertNext(w -> assertThat(w.getBalance()).isEqualByComparingTo("30.00"))
                .verifyComplete();
        StepVerifier.create(walletService.getTransactions(buyer.getId(), 0, 10))
                .assertNext(page -> assertThat(page.getTotalElements()).isEqualTo(2))
                .verifyComplete();
        StepVerifier.create(orderRepository.findById(orderId))
                .assertNext(o -> assertThat(o.getPaymentStatus())
                        .isEqualTo(Order.PaymentStatus.PARTIALLY_REFUNDED))
                .verifyComplete();
    }

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------

    private User saveUser(String email, User.UserRole role) {
        User user = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .password("{noop}irrelevant")
                .firstName("Test")
                .lastName(role.name().toLowerCase())
                .role(role)
                .isActive(true)
                .emailVerified(true)
                .build()).block();
        assertThat(user).isNotNull();
        return user;
    }

    private Product saveProduct(String suffix, BigDecimal price, UUID categoryId, UUID vendorId) {
        Product product = productRepository.save(Product.builder()
                .id(UUID.randomUUID())
                .name("IT Product " + suffix)
                .description("integration test product")
                .price(price)
                .categoryId(categoryId)
                .stock(100)
                .sku("IT-SKU-" + suffix)
                .isActive(true)
                .vendorId(vendorId)
                .build()).block();
        assertThat(product).isNotNull();
        return product;
    }

    private AddressDto address() {
        return AddressDto.builder()
                .firstName("Test")
                .lastName("Buyer")
                .addressLine1("1 Integration Way")
                .city("Dhaka")
                .state("Dhaka")
                .postalCode("1207")
                .country("BD")
                .phone("+8801000000000")
                .build();
    }
}
