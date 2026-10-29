package com.bookstore.ecommerce.member;

import java.util.UUID;

/** Gift point operations used by the payment domain; same transactional contract as {@link WalletService}. */
public interface GiftPointService {

    long pointsBalance(UUID userId);

    /** Redeems points for a payment. @throws com.bookstore.ecommerce.common.ApiException INSUFFICIENT_GIFT_POINTS */
    void redeemForPayment(UUID userId, long points, UUID orderId, UUID paymentId);

    /** Credits points back for a refund. */
    void refund(UUID userId, long points, UUID orderId, UUID paymentId, UUID refundId);
}
