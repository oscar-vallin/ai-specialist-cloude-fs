package com.bookstore.ecommerce.common;

import java.util.Locale;

/**
 * Stable machine-readable error codes. Must stay identical to the {@code ErrorCode} enum of
 * {@code api/openapi.yaml} (checked by the contract test).
 */
public enum ErrorCode {
    VALIDATION_FAILED(400),
    MALFORMED_REQUEST(400),
    IDEMPOTENCY_KEY_REQUIRED(400),
    UNAUTHENTICATED(401),
    INVALID_CREDENTIALS(401),
    ACCOUNT_DISABLED(403),
    FORBIDDEN(403),
    ADDRESS_NOT_FOUND(404),
    PRODUCT_NOT_FOUND(404),
    STORE_NOT_FOUND(404),
    CART_NOT_FOUND(404),
    CART_ITEM_NOT_FOUND(404),
    ORDER_NOT_FOUND(404),
    PAYMENT_NOT_FOUND(404),
    SHIPMENT_NOT_FOUND(404),
    RESOURCE_NOT_FOUND(404),
    METHOD_NOT_ALLOWED(405),
    EMAIL_ALREADY_REGISTERED(409),
    INSUFFICIENT_STOCK(409),
    PRICE_CHANGED(409),
    IDEMPOTENCY_KEY_REUSED(409),
    ORDER_INVALID_STATE(409),
    PAYMENT_INVALID_STATE(409),
    SHIPMENT_INVALID_STATE(409),
    CONCURRENT_MODIFICATION(409),
    CART_EMPTY(422),
    COUPON_INVALID(422),
    PRODUCT_UNAVAILABLE(422),
    SHIPPING_UNAVAILABLE(422),
    CANCELLATION_WINDOW_EXPIRED(422),
    RETURN_WINDOW_EXPIRED(422),
    PAYMENT_DECLINED(422),
    PAYMENT_WINDOW_EXPIRED(422),
    PAYMENT_AMOUNT_MISMATCH(422),
    INSUFFICIENT_WALLET_BALANCE(422),
    INSUFFICIENT_GIFT_POINTS(422),
    GIFT_POINTS_LIMIT_EXCEEDED(422),
    REFUND_EXCEEDS_PAYMENT(422),
    INTERNAL_ERROR(500);

    private final int status;

    ErrorCode(int status) {
        this.status = status;
    }

    public int status() {
        return status;
    }

    /** Human-readable title, for example "Cancellation window expired". */
    public String title() {
        String lower = name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /** Problem type URI suffix, for example "cancellation-window-expired". */
    public String slug() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
