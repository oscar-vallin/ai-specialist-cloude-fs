package com.bookstore.ecommerce.payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PaymentRepository {

    record PaymentRow(
            UUID id,
            UUID orderId,
            String method,
            String status,
            BigDecimal amount,
            Long pointsUsed,
            String cardBrand,
            String cardLast4,
            String gatewayReference,
            String failureReason,
            String idempotencyKey,
            String requestHash,
            OffsetDateTime processedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    record RefundRow(
            UUID id,
            UUID paymentId,
            UUID orderId,
            UUID returnId,
            BigDecimal amount,
            String status,
            String reason,
            String idempotencyKey,
            String requestHash,
            OffsetDateTime refundedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    private static final RowMapper<PaymentRow> PAYMENT_MAPPER = (rs, n) -> new PaymentRow(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getString("method"),
            rs.getString("status"),
            rs.getBigDecimal("amount"),
            rs.getObject("points_used") != null ? ((Number) rs.getObject("points_used")).longValue() : null,
            rs.getString("card_brand"),
            rs.getString("card_last4"),
            rs.getString("gateway_reference"),
            rs.getString("failure_reason"),
            rs.getString("idempotency_key"),
            rs.getString("request_hash"),
            rs.getObject("processed_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    private static final RowMapper<RefundRow> REFUND_MAPPER = (rs, n) -> new RefundRow(
            rs.getObject("id", UUID.class),
            rs.getObject("payment_id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getObject("return_id", UUID.class),
            rs.getBigDecimal("amount"),
            rs.getString("status"),
            rs.getString("reason"),
            rs.getString("idempotency_key"),
            rs.getString("request_hash"),
            rs.getObject("refunded_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    private final JdbcClient jdbc;

    PaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<PaymentRow> findByIdempotencyKey(UUID orderId, String method, String idempotencyKey) {
        return jdbc.sql("""
                SELECT * FROM payments
                WHERE order_id = :orderId AND method = :method AND idempotency_key = :key""")
                .param("orderId", orderId)
                .param("method", method)
                .param("key", idempotencyKey)
                .query(PAYMENT_MAPPER)
                .optional();
    }

    Optional<PaymentRow> findById(UUID id) {
        return jdbc.sql("SELECT * FROM payments WHERE id = :id")
                .param("id", id)
                .query(PAYMENT_MAPPER)
                .optional();
    }

    Optional<PaymentRow> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT * FROM payments WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(PAYMENT_MAPPER)
                .optional();
    }

    List<PaymentRow> findByOrderId(UUID orderId) {
        return jdbc.sql("SELECT * FROM payments WHERE order_id = :orderId ORDER BY created_at ASC")
                .param("orderId", orderId)
                .query(PAYMENT_MAPPER)
                .list();
    }

    UUID insertPayment(
            UUID orderId,
            String method,
            String status,
            BigDecimal amount,
            Long pointsUsed,
            String cardBrand,
            String cardLast4,
            String gatewayReference,
            String failureReason,
            String idempotencyKey,
            String requestHash,
            OffsetDateTime processedAt) {
        return jdbc.sql("""
                INSERT INTO payments (
                    order_id, method, status, amount, points_used,
                    card_brand, card_last4, gateway_reference, failure_reason,
                    idempotency_key, request_hash, processed_at
                ) VALUES (
                    :orderId, :method, :status, :amount, :pointsUsed,
                    :cardBrand, :cardLast4, :gatewayReference, :failureReason,
                    :idempotencyKey, :requestHash, :processedAt
                ) RETURNING id""")
                .param("orderId", orderId)
                .param("method", method)
                .param("status", status)
                .param("amount", amount)
                .param("pointsUsed", pointsUsed)
                .param("cardBrand", cardBrand)
                .param("cardLast4", cardLast4)
                .param("gatewayReference", gatewayReference)
                .param("failureReason", failureReason)
                .param("idempotencyKey", idempotencyKey)
                .param("requestHash", requestHash)
                .param("processedAt", processedAt)
                .query(UUID.class)
                .single();
    }

    void updatePaymentStatus(UUID id, String status, String failureReason, OffsetDateTime processedAt) {
        jdbc.sql("""
                UPDATE payments
                SET status = :status,
                    failure_reason = COALESCE(:failureReason, failure_reason),
                    processed_at = COALESCE(:processedAt, processed_at)
                WHERE id = :id""")
                .param("id", id)
                .param("status", status)
                .param("failureReason", failureReason)
                .param("processedAt", processedAt)
                .update();
    }

    Optional<RefundRow> findRefundByIdempotencyKey(UUID paymentId, String idempotencyKey) {
        return jdbc.sql("""
                SELECT * FROM refunds
                WHERE payment_id = :paymentId AND idempotency_key = :key""")
                .param("paymentId", paymentId)
                .param("key", idempotencyKey)
                .query(REFUND_MAPPER)
                .optional();
    }

    List<RefundRow> findRefundsByPaymentId(UUID paymentId) {
        return jdbc.sql("SELECT * FROM refunds WHERE payment_id = :paymentId ORDER BY created_at ASC")
                .param("paymentId", paymentId)
                .query(REFUND_MAPPER)
                .list();
    }

    UUID insertRefund(
            UUID paymentId,
            UUID orderId,
            UUID returnId,
            BigDecimal amount,
            String status,
            String reason,
            String idempotencyKey,
            String requestHash,
            OffsetDateTime refundedAt) {
        return jdbc.sql("""
                INSERT INTO refunds (
                    payment_id, order_id, return_id, amount, status, reason,
                    idempotency_key, request_hash, refunded_at
                ) VALUES (
                    :paymentId, :orderId, :returnId, :amount, :status, :reason,
                    :idempotencyKey, :requestHash, :refundedAt
                ) RETURNING id""")
                .param("paymentId", paymentId)
                .param("orderId", orderId)
                .param("returnId", returnId)
                .param("amount", amount)
                .param("status", status)
                .param("reason", reason)
                .param("idempotencyKey", idempotencyKey)
                .param("requestHash", requestHash)
                .param("refundedAt", refundedAt)
                .query(UUID.class)
                .single();
    }

    BigDecimal sumRefundedByOrderId(UUID orderId) {
        return jdbc.sql("SELECT COALESCE(SUM(amount), 0.00) FROM refunds WHERE order_id = :orderId AND status = 'COMPLETED'")
                .param("orderId", orderId)
                .query(BigDecimal.class)
                .single();
    }

    BigDecimal sumRefundedByPaymentId(UUID paymentId) {
        return jdbc.sql("SELECT COALESCE(SUM(amount), 0.00) FROM refunds WHERE payment_id = :paymentId AND status = 'COMPLETED'")
                .param("paymentId", paymentId)
                .query(BigDecimal.class)
                .single();
    }
}
