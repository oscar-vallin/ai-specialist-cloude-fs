package com.bookstore.ecommerce.member;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Wallet operations used by the payment domain. Every call must run inside the caller's transaction and
 * appends a {@code wallet_transactions} row in the same transaction as the balance change.
 */
public interface WalletService {

    BigDecimal balance(UUID userId);

    /** Debits {@code amount} for a payment. @throws com.bookstore.ecommerce.common.ApiException INSUFFICIENT_WALLET_BALANCE */
    void debitForPayment(UUID userId, BigDecimal amount, UUID orderId, UUID paymentId);

    /** Credits {@code amount} back for a refund. */
    void creditForRefund(UUID userId, BigDecimal amount, UUID orderId, UUID paymentId, UUID refundId);
}
