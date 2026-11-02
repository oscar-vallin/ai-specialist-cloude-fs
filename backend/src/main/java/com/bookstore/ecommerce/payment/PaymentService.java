package com.bookstore.ecommerce.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** What the order domain may ask the payment domain. */
public interface PaymentService {

    /**
     * Refunds, in full, what is still refundable on every captured payment of the order, each to its original
     * method (FR-P4). Joins the caller's transaction.
     *
     * @param returnId the return that caused the refund, or null for a cancellation
     * @return the amount refunded now
     */
    BigDecimal refundAllForOrder(UUID orderId, UUID returnId, String reason);

    List<UUID> paymentIdsOf(UUID orderId);

    /** Sum of the completed refunds of the order. */
    BigDecimal refundedTotal(UUID orderId);
}
