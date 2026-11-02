package com.bookstore.ecommerce.payment;

import java.math.BigDecimal;

/** Gateway abstraction for processing card payments (FR-P1). */
public interface PaymentGateway {

    record ChargeRequest(
            BigDecimal amount,
            String currency,
            String cardNumber,
            int expiryMonth,
            int expiryYear,
            String cvv) {
    }

    record RefundRequest(
            String gatewayReference,
            BigDecimal amount,
            String currency) {
    }

    record GatewayResponse(
            boolean successful,
            String reference,
            String failureReason) {
    }

    GatewayResponse authorizeAndCapture(ChargeRequest request);

    GatewayResponse refund(RefundRequest request);
}
