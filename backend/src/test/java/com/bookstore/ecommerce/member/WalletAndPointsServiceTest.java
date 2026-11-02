package com.bookstore.ecommerce.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.ErrorCode;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WalletAndPointsServiceTest {

    private MemberRepository repository;
    private WalletAndPointsService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();
    private final UUID refundId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(MemberRepository.class);
        service = new WalletAndPointsService(repository);
    }

    @Test
    void balanceReturnsWalletBalanceWhenPresent() {
        when(repository.findWallet(userId))
                .thenReturn(Optional.of(new MemberRepository.WalletRow(walletId, new BigDecimal("150.00"))));

        BigDecimal balance = service.balance(userId);

        assertThat(balance).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void balanceReturnsZeroWhenNoWalletExists() {
        when(repository.findWallet(userId)).thenReturn(Optional.empty());

        BigDecimal balance = service.balance(userId);

        assertThat(balance).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void debitForPaymentSucceedsWhenSufficientFunds() {
        when(repository.lockWallet(userId))
                .thenReturn(Optional.of(new MemberRepository.WalletRow(walletId, new BigDecimal("100.00"))));

        service.debitForPayment(userId, new BigDecimal("40.00"), orderId, paymentId);

        verify(repository).setWalletBalance(walletId, new BigDecimal("60.00"));
        verify(repository).insertWalletTransaction(walletId, "DEBIT", new BigDecimal("40.00"), "PAYMENT", orderId,
                paymentId, null, new BigDecimal("60.00"));
    }

    @Test
    void debitForPaymentThrowsWhenWalletNotFound() {
        when(repository.lockWallet(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.debitForPayment(userId, new BigDecimal("10.00"), orderId, paymentId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.INSUFFICIENT_WALLET_BALANCE));
    }

    @Test
    void debitForPaymentThrowsWhenInsufficientBalance() {
        when(repository.lockWallet(userId))
                .thenReturn(Optional.of(new MemberRepository.WalletRow(walletId, new BigDecimal("25.00"))));

        assertThatThrownBy(() -> service.debitForPayment(userId, new BigDecimal("50.00"), orderId, paymentId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.INSUFFICIENT_WALLET_BALANCE));
    }

    @Test
    void creditForRefundSucceeds() {
        when(repository.lockWallet(userId))
                .thenReturn(Optional.of(new MemberRepository.WalletRow(walletId, new BigDecimal("60.00"))));

        service.creditForRefund(userId, new BigDecimal("40.00"), orderId, paymentId, refundId);

        verify(repository).setWalletBalance(walletId, new BigDecimal("100.00"));
        verify(repository).insertWalletTransaction(walletId, "CREDIT", new BigDecimal("40.00"), "REFUND", orderId,
                paymentId, refundId, new BigDecimal("100.00"));
    }

    @Test
    void creditForRefundThrowsWhenWalletMissing() {
        when(repository.lockWallet(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.creditForRefund(userId, new BigDecimal("40.00"), orderId, paymentId, refundId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Wallet missing");
    }

    @Test
    void pointsBalanceReturnsPointsWhenPresent() {
        when(repository.findGiftPointBalance(userId)).thenReturn(Optional.of(500L));

        long points = service.pointsBalance(userId);

        assertThat(points).isEqualTo(500L);
    }

    @Test
    void pointsBalanceReturnsZeroWhenEmpty() {
        when(repository.findGiftPointBalance(userId)).thenReturn(Optional.empty());

        long points = service.pointsBalance(userId);

        assertThat(points).isEqualTo(0L);
    }

    @Test
    void redeemForPaymentSucceedsWhenSufficientPoints() {
        when(repository.lockGiftPointBalance(userId)).thenReturn(Optional.of(1000L));

        service.redeemForPayment(userId, 300L, orderId, paymentId);

        verify(repository).setGiftPointBalance(userId, 700L);
        verify(repository).insertGiftPointLedger(userId, -300L, "REDEEMED", orderId, paymentId, null, 700L);
    }

    @Test
    void redeemForPaymentThrowsWhenInsufficientPoints() {
        when(repository.lockGiftPointBalance(userId)).thenReturn(Optional.of(100L));

        assertThatThrownBy(() -> service.redeemForPayment(userId, 500L, orderId, paymentId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.INSUFFICIENT_GIFT_POINTS));
    }

    @Test
    void redeemForPaymentThrowsWhenNoPointsRecord() {
        when(repository.lockGiftPointBalance(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeemForPayment(userId, 100L, orderId, paymentId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.INSUFFICIENT_GIFT_POINTS));
    }

    @Test
    void refundPointsSucceeds() {
        when(repository.lockGiftPointBalance(userId)).thenReturn(Optional.of(200L));

        service.refund(userId, 300L, orderId, paymentId, refundId);

        verify(repository).setGiftPointBalance(userId, 500L);
        verify(repository).insertGiftPointLedger(userId, 300L, "REFUNDED", orderId, paymentId, refundId, 500L);
    }
}
