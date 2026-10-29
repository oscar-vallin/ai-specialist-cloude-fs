package com.bookstore.ecommerce.member;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.ErrorCode;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Balance of record plus append-only history, changed together under a row lock so two concurrent payments can
 * never spend the same money or points (docs/data-model.md section 6.2, rules 6 and 7).
 */
@Service
class WalletAndPointsService implements WalletService, GiftPointService {

    private final MemberRepository repository;

    WalletAndPointsService(MemberRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal balance(UUID userId) {
        return repository.findWallet(userId).map(MemberRepository.WalletRow::balance).orElse(BigDecimal.ZERO);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void debitForPayment(UUID userId, BigDecimal amount, UUID orderId, UUID paymentId) {
        var wallet = repository.lockWallet(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.INSUFFICIENT_WALLET_BALANCE, "The wallet has no funds."));
        if (wallet.balance().compareTo(amount) < 0) {
            throw ApiException.of(ErrorCode.INSUFFICIENT_WALLET_BALANCE, "The wallet balance is too low for this payment.");
        }
        BigDecimal after = wallet.balance().subtract(amount);
        repository.setWalletBalance(wallet.id(), after);
        repository.insertWalletTransaction(wallet.id(), "DEBIT", amount, "PAYMENT", orderId, paymentId, null, after);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void creditForRefund(UUID userId, BigDecimal amount, UUID orderId, UUID paymentId, UUID refundId) {
        var wallet = repository.lockWallet(userId)
                .orElseThrow(() -> new IllegalStateException("Wallet missing for user " + userId));
        BigDecimal after = wallet.balance().add(amount);
        repository.setWalletBalance(wallet.id(), after);
        repository.insertWalletTransaction(wallet.id(), "CREDIT", amount, "REFUND", orderId, paymentId, refundId, after);
    }

    @Override
    @Transactional(readOnly = true)
    public long pointsBalance(UUID userId) {
        return repository.findGiftPointBalance(userId).orElse(0L);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void redeemForPayment(UUID userId, long points, UUID orderId, UUID paymentId) {
        long balance = repository.lockGiftPointBalance(userId).orElse(0L);
        if (balance < points) {
            throw ApiException.of(ErrorCode.INSUFFICIENT_GIFT_POINTS, "Not enough gift points for this payment.");
        }
        long after = balance - points;
        repository.setGiftPointBalance(userId, after);
        repository.insertGiftPointLedger(userId, -points, "REDEEMED", orderId, paymentId, null, after);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void refund(UUID userId, long points, UUID orderId, UUID paymentId, UUID refundId) {
        long balance = repository.lockGiftPointBalance(userId).orElse(0L);
        long after = balance + points;
        repository.setGiftPointBalance(userId, after);
        repository.insertGiftPointLedger(userId, points, "REFUNDED", orderId, paymentId, refundId, after);
    }
}
