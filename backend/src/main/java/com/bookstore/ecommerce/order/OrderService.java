package com.bookstore.ecommerce.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * What the payment and shipping domains may ask the order domain. Methods that change an order join the caller's
 * transaction ({@code MANDATORY}), so a payment and the order confirmation commit together.
 */
public interface OrderService {

    /** Owner and store of an order, whoever asks. */
    Optional<OrderRef> findRef(UUID orderId);

    /** Locks one of the user's orders for payment. @throws com.bookstore.ecommerce.common.ApiException ORDER_NOT_FOUND */
    PayableOrder lockForPayment(UUID orderId, UUID userId);

    OrderDtos.Order view(UUID orderId);

    /** Payment captured: PENDING_PAYMENT to CONFIRMED, stock sold, cart converted, outbound shipment created. */
    void confirmPaid(UUID orderId);

    /** Payment declined or expired: PENDING_PAYMENT to PAYMENT_FAILED and the stock reservation is released. */
    void markPaymentFailed(UUID orderId);

    /** Outbound shipment in transit: CONFIRMED to SHIPPED. */
    void markShipped(UUID orderId);

    /** Outbound shipment delivered: SHIPPED to DELIVERED; the return deadline is frozen here. */
    void markDelivered(UUID orderId);

    /** The open return request of an order in RETURN_REQUESTED, if its return is still REQUESTED. */
    Optional<UUID> pendingReturn(UUID orderId);

    void approveReturn(UUID returnId);

    /** Return parcel received: refund every payment, restock, RETURN_REQUESTED to RETURNED. */
    void completeReturn(UUID orderId);

    record OrderRef(UUID id, UUID userId, UUID storeId) {
    }

    record PayableOrder(UUID id, UUID userId, UUID storeId, String status, BigDecimal total, String currency,
            OffsetDateTime paymentExpiresAt) {
    }
}
