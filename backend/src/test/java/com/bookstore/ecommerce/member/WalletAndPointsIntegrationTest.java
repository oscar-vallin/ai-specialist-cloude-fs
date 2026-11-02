package com.bookstore.ecommerce.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class WalletAndPointsIntegrationTest extends IntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private GiftPointService giftPointService;

    @Autowired
    private TransactionTemplate tx;

    private UUID customerId() {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, CUSTOMER_EMAIL);
    }

    @Test
    void debitAndCreditWalletFlow() {
        UUID userId = customerId();
        BigDecimal initialBalance = walletService.balance(userId);

        tx.executeWithoutResult(status -> walletService.debitForPayment(userId, new BigDecimal("15.00"), null, null));
        assertThat(walletService.balance(userId)).isEqualByComparingTo(initialBalance.subtract(new BigDecimal("15.00")));

        tx.executeWithoutResult(status -> walletService.creditForRefund(userId, new BigDecimal("15.00"), null, null, null));
        assertThat(walletService.balance(userId)).isEqualByComparingTo(initialBalance);

        int debits = jdbc.queryForObject("SELECT count(*) FROM wallet_transactions WHERE wallet_id = (SELECT id FROM wallets WHERE user_id = ?) AND type = 'DEBIT'",
                Integer.class, userId);
        int credits = jdbc.queryForObject("SELECT count(*) FROM wallet_transactions WHERE wallet_id = (SELECT id FROM wallets WHERE user_id = ?) AND type = 'CREDIT'",
                Integer.class, userId);
        assertThat(debits).isGreaterThanOrEqualTo(1);
        assertThat(credits).isGreaterThanOrEqualTo(1);
    }

    @Test
    void debitWalletThrowsWhenInsufficientBalance() {
        UUID userId = customerId();
        BigDecimal currentBalance = walletService.balance(userId);

        assertThatThrownBy(() -> tx.executeWithoutResult(
                status -> walletService.debitForPayment(userId, currentBalance.add(new BigDecimal("1000.00")), null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.INSUFFICIENT_WALLET_BALANCE));
    }

    @Test
    void redeemAndRefundGiftPointsFlow() {
        UUID userId = customerId();
        long initialPoints = giftPointService.pointsBalance(userId);

        tx.executeWithoutResult(status -> giftPointService.redeemForPayment(userId, 500L, null, null));
        assertThat(giftPointService.pointsBalance(userId)).isEqualTo(initialPoints - 500L);

        tx.executeWithoutResult(status -> giftPointService.refund(userId, 500L, null, null, null));
        assertThat(giftPointService.pointsBalance(userId)).isEqualTo(initialPoints);

        int redeems = jdbc.queryForObject("SELECT count(*) FROM gift_point_ledger WHERE user_id = ? AND type = 'REDEEMED'",
                Integer.class, userId);
        int refunds = jdbc.queryForObject("SELECT count(*) FROM gift_point_ledger WHERE user_id = ? AND type = 'REFUNDED'",
                Integer.class, userId);
        assertThat(redeems).isGreaterThanOrEqualTo(1);
        assertThat(refunds).isGreaterThanOrEqualTo(1);
    }

    @Test
    void redeemGiftPointsThrowsWhenInsufficientPoints() {
        UUID userId = customerId();
        long currentPoints = giftPointService.pointsBalance(userId);

        assertThatThrownBy(() -> tx.executeWithoutResult(
                status -> giftPointService.redeemForPayment(userId, currentPoints + 50000L, null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.INSUFFICIENT_GIFT_POINTS));
    }
}
