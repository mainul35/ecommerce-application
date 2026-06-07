package com.ecommerce.config.converter;

import com.ecommerce.model.Dispute;
import com.ecommerce.model.DisputeAttachment;
import com.ecommerce.model.DisputeMessage;
import com.ecommerce.model.EscrowTransaction;
import com.ecommerce.model.NumericEnum;
import com.ecommerce.model.Order;
import com.ecommerce.model.ReturnRequest;
import com.ecommerce.model.WalletTransaction;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

import java.util.List;

/**
 * R2DBC converters mapping {@link NumericEnum} implementations to/from their
 * SMALLINT codes. Registered in {@link com.ecommerce.config.R2dbcConfig}.
 *
 * One explicit converter pair per enum: Spring Data resolves converters by
 * concrete ConvertiblePair, so a shared generic instance would lose its type
 * arguments to erasure. The Postgres driver decodes SMALLINT as {@link Short},
 * hence readers convert from Short (widened via Number for safety).
 *
 * Adding a new status enum = one writer + one reader class + two list entries.
 */
public final class NumericEnumConverters {

    private NumericEnumConverters() {
    }

    /** Everything R2dbcConfig needs to register, in one place. */
    public static List<Object> all() {
        return List.of(
                new OrderStatusWriter(), new OrderStatusReader(),
                new PaymentStatusWriter(), new PaymentStatusReader(),
                new EscrowStatusWriter(), new EscrowStatusReader(),
                new DisputeStatusWriter(), new DisputeStatusReader(),
                new AuthorRoleWriter(), new AuthorRoleReader(),
                new AttachmentTypeWriter(), new AttachmentTypeReader(),
                new WalletTxTypeWriter(), new WalletTxTypeReader(),
                new WalletRefTypeWriter(), new WalletRefTypeReader(),
                new ReturnStatusWriter(), new ReturnStatusReader(),
                new RefundDestinationWriter(), new RefundDestinationReader()
        );
    }

    // ---- Order.OrderStatus ----
    @WritingConverter
    public static class OrderStatusWriter implements Converter<Order.OrderStatus, Integer> {
        @Override public Integer convert(Order.OrderStatus source) { return source.getCode(); }
    }

    @ReadingConverter
    public static class OrderStatusReader implements Converter<Number, Order.OrderStatus> {
        @Override public Order.OrderStatus convert(Number source) {
            return NumericEnum.fromCode(Order.OrderStatus.class, source.intValue());
        }
    }

    // ---- Order.PaymentStatus ----
    @WritingConverter
    public static class PaymentStatusWriter implements Converter<Order.PaymentStatus, Integer> {
        @Override public Integer convert(Order.PaymentStatus source) { return source.getCode(); }
    }

    @ReadingConverter
    public static class PaymentStatusReader implements Converter<Number, Order.PaymentStatus> {
        @Override public Order.PaymentStatus convert(Number source) {
            return NumericEnum.fromCode(Order.PaymentStatus.class, source.intValue());
        }
    }

    // ---- EscrowTransaction.EscrowStatus ----
    @WritingConverter
    public static class EscrowStatusWriter implements Converter<EscrowTransaction.EscrowStatus, Integer> {
        @Override public Integer convert(EscrowTransaction.EscrowStatus source) { return source.getCode(); }
    }

    @ReadingConverter
    public static class EscrowStatusReader implements Converter<Number, EscrowTransaction.EscrowStatus> {
        @Override public EscrowTransaction.EscrowStatus convert(Number source) {
            return NumericEnum.fromCode(EscrowTransaction.EscrowStatus.class, source.intValue());
        }
    }

    // ---- Dispute.DisputeStatus ----
    @WritingConverter
    public static class DisputeStatusWriter implements Converter<Dispute.DisputeStatus, Integer> {
        @Override public Integer convert(Dispute.DisputeStatus source) { return source.getCode(); }
    }

    @ReadingConverter
    public static class DisputeStatusReader implements Converter<Number, Dispute.DisputeStatus> {
        @Override public Dispute.DisputeStatus convert(Number source) {
            return NumericEnum.fromCode(Dispute.DisputeStatus.class, source.intValue());
        }
    }

    // ---- DisputeMessage.AuthorRole ----
    @WritingConverter
    public static class AuthorRoleWriter implements Converter<DisputeMessage.AuthorRole, Integer> {
        @Override public Integer convert(DisputeMessage.AuthorRole source) { return source.getCode(); }
    }

    @ReadingConverter
    public static class AuthorRoleReader implements Converter<Number, DisputeMessage.AuthorRole> {
        @Override public DisputeMessage.AuthorRole convert(Number source) {
            return NumericEnum.fromCode(DisputeMessage.AuthorRole.class, source.intValue());
        }
    }

    // ---- DisputeAttachment.AttachmentType ----
    @WritingConverter
    public static class AttachmentTypeWriter implements Converter<DisputeAttachment.AttachmentType, Integer> {
        @Override public Integer convert(DisputeAttachment.AttachmentType source) { return source.getCode(); }
    }

    @ReadingConverter
    public static class AttachmentTypeReader implements Converter<Number, DisputeAttachment.AttachmentType> {
        @Override public DisputeAttachment.AttachmentType convert(Number source) {
            return NumericEnum.fromCode(DisputeAttachment.AttachmentType.class, source.intValue());
        }
    }

    // ---- WalletTransaction.TransactionType ----
    @WritingConverter
    public static class WalletTxTypeWriter implements Converter<WalletTransaction.TransactionType, Integer> {
        @Override public Integer convert(WalletTransaction.TransactionType source) { return source.getCode(); }
    }

    @ReadingConverter
    public static class WalletTxTypeReader implements Converter<Number, WalletTransaction.TransactionType> {
        @Override public WalletTransaction.TransactionType convert(Number source) {
            return NumericEnum.fromCode(WalletTransaction.TransactionType.class, source.intValue());
        }
    }

    // ---- WalletTransaction.ReferenceType ----
    @WritingConverter
    public static class WalletRefTypeWriter implements Converter<WalletTransaction.ReferenceType, Integer> {
        @Override public Integer convert(WalletTransaction.ReferenceType source) { return source.getCode(); }
    }

    @ReadingConverter
    public static class WalletRefTypeReader implements Converter<Number, WalletTransaction.ReferenceType> {
        @Override public WalletTransaction.ReferenceType convert(Number source) {
            return NumericEnum.fromCode(WalletTransaction.ReferenceType.class, source.intValue());
        }
    }

    // ---- ReturnRequest.ReturnStatus ----
    @WritingConverter
    public static class ReturnStatusWriter implements Converter<ReturnRequest.ReturnStatus, Integer> {
        @Override public Integer convert(ReturnRequest.ReturnStatus source) { return source.getCode(); }
    }

    @ReadingConverter
    public static class ReturnStatusReader implements Converter<Number, ReturnRequest.ReturnStatus> {
        @Override public ReturnRequest.ReturnStatus convert(Number source) {
            return NumericEnum.fromCode(ReturnRequest.ReturnStatus.class, source.intValue());
        }
    }

    // ---- ReturnRequest.RefundDestination ----
    @WritingConverter
    public static class RefundDestinationWriter implements Converter<ReturnRequest.RefundDestination, Integer> {
        @Override public Integer convert(ReturnRequest.RefundDestination source) { return source.getCode(); }
    }

    @ReadingConverter
    public static class RefundDestinationReader implements Converter<Number, ReturnRequest.RefundDestination> {
        @Override public ReturnRequest.RefundDestination convert(Number source) {
            return NumericEnum.fromCode(ReturnRequest.RefundDestination.class, source.intValue());
        }
    }
}
