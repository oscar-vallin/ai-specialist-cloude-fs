package com.bookstore.ecommerce.payment;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.Caller;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.config.AppProperties;
import com.bookstore.ecommerce.member.GiftPointService;
import com.bookstore.ecommerce.member.WalletService;
import com.bookstore.ecommerce.order.OrderDtos.Order;
import com.bookstore.ecommerce.order.OrderService;
import com.bookstore.ecommerce.order.OrderService.OrderRef;
import com.bookstore.ecommerce.order.OrderService.PayableOrder;
import com.bookstore.ecommerce.payment.PaymentDtos.CardDetails;
import com.bookstore.ecommerce.payment.PaymentDtos.PayOrderRequest;
import com.bookstore.ecommerce.payment.PaymentDtos.Payment;
import com.bookstore.ecommerce.payment.PaymentDtos.PaymentLeg;
import com.bookstore.ecommerce.payment.PaymentDtos.PaymentResult;
import com.bookstore.ecommerce.payment.PaymentDtos.Refund;
import com.bookstore.ecommerce.payment.PaymentDtos.RefundRequest;
import com.bookstore.ecommerce.payment.PaymentGateway.ChargeRequest;
import com.bookstore.ecommerce.payment.PaymentGateway.GatewayResponse;
import com.bookstore.ecommerce.payment.PaymentRepository.PaymentRow;
import com.bookstore.ecommerce.payment.PaymentRepository.RefundRow;
import com.bookstore.ecommerce.store.StoreService;
import com.bookstore.ecommerce.store.StoreService.StorePolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository repository;
    private final PaymentGateway gateway;
    private final OrderService orderService;
    private final WalletService walletService;
    private final GiftPointService giftPointService;
    private final StoreService storeService;
    private final AppProperties properties;
    private final Clock clock;

    PaymentServiceImpl(
            PaymentRepository repository,
            PaymentGateway gateway,
            OrderService orderService,
            WalletService walletService,
            GiftPointService giftPointService,
            StoreService storeService,
            AppProperties properties,
            Clock clock) {
        this.repository = repository;
        this.gateway = gateway;
        this.orderService = orderService;
        this.walletService = walletService;
        this.giftPointService = giftPointService;
        this.storeService = storeService;
        this.properties = properties;
        this.clock = clock;
    }

    public record PayOrderResponse(PaymentResult result, boolean replayed) {
    }

    public record RefundResult(Refund refund, boolean replayed) {
    }

    @Transactional
    public PayOrderResponse payOrder(Caller caller, String idempotencyKey, PayOrderRequest request) {
        UUID userId = caller.requireUser();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.of(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key header is required.");
        }

        String requestHash = hashPayOrderRequest(request);
        List<PaymentRow> existingPayments = repository.findByOrderId(request.orderId());
        Optional<PaymentRow> matchingKeyPayment = existingPayments.stream()
                .filter(p -> idempotencyKey.equals(p.idempotencyKey()))
                .findFirst();

        if (matchingKeyPayment.isPresent()) {
            PaymentRow sample = matchingKeyPayment.get();
            if (requestHash.equals(sample.requestHash())) {
                Order order = orderService.view(request.orderId());
                List<Payment> payments = getPaymentsForOrder(request.orderId());
                return new PayOrderResponse(new PaymentResult(order, payments), true);
            } else {
                throw ApiException.of(ErrorCode.IDEMPOTENCY_KEY_REUSED, "Idempotency key has already been used with different request parameters.");
            }
        }

        PayableOrder order = orderService.lockForPayment(request.orderId(), userId);
        if (!"PENDING_PAYMENT".equals(order.status())) {
            throw ApiException.of(ErrorCode.ORDER_INVALID_STATE, "Order is not pending payment: " + order.status());
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (order.paymentExpiresAt() != null && now.isAfter(order.paymentExpiresAt())) {
            orderService.markPaymentFailed(order.id());
            throw ApiException.of(ErrorCode.PAYMENT_WINDOW_EXPIRED, "Payment window has expired.");
        }

        StorePolicy policy = storeService.policy(order.storeId());
        int pointsRate = policy.giftPointsPerCurrencyUnit();

        // Validate legs: at most 1 card, 1 wallet, 1 gift points
        Set<String> methodsSeen = new HashSet<>();
        PaymentLeg cardLeg = null;
        PaymentLeg walletLeg = null;
        PaymentLeg pointsLeg = null;

        for (PaymentLeg leg : request.legs()) {
            String m = leg.method();
            String family = ("CREDIT_CARD".equals(m) || "DEBIT_CARD".equals(m)) ? "CARD" : m;
            if (!methodsSeen.add(family)) {
                throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Multiple legs of the same method family are not allowed: " + m);
            }
            if ("CARD".equals(family)) {
                cardLeg = leg;
            } else if ("WALLET".equals(m)) {
                walletLeg = leg;
            } else if ("GIFT_POINTS".equals(m)) {
                pointsLeg = leg;
            }
        }

        BigDecimal walletAmount = BigDecimal.ZERO;
        if (walletLeg != null) {
            if (walletLeg.amount() == null || walletLeg.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Wallet payment amount must be greater than zero.");
            }
            walletAmount = walletLeg.amount();
            BigDecimal balance = walletService.balance(userId);
            if (balance.compareTo(walletAmount) < 0) {
                throw ApiException.of(ErrorCode.INSUFFICIENT_WALLET_BALANCE, "Insufficient wallet balance.");
            }
        }

        BigDecimal pointsAmount = BigDecimal.ZERO;
        long pointsUsed = 0;
        if (pointsLeg != null) {
            if (pointsLeg.points() == null || pointsLeg.points() <= 0) {
                throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Gift points must be greater than zero.");
            }
            pointsUsed = pointsLeg.points();
            long userPoints = giftPointService.pointsBalance(userId);
            if (userPoints < pointsUsed) {
                throw ApiException.of(ErrorCode.INSUFFICIENT_GIFT_POINTS, "Insufficient gift points balance.");
            }

            long maxPointsAllowed = order.total().multiply(BigDecimal.valueOf(pointsRate))
                    .multiply(BigDecimal.valueOf(policy.maxGiftPointsPercent()))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                    .longValue();
            if (pointsUsed > maxPointsAllowed) {
                throw ApiException.of(ErrorCode.GIFT_POINTS_LIMIT_EXCEEDED, "Gift points redeem amount exceeds store policy limit.");
            }

            pointsAmount = BigDecimal.valueOf(pointsUsed).divide(BigDecimal.valueOf(pointsRate), 2, RoundingMode.DOWN);
        }

        BigDecimal nonCardSum = walletAmount.add(pointsAmount);
        BigDecimal cardAmount = BigDecimal.ZERO;

        if (cardLeg != null) {
            if (cardLeg.card() == null) {
                throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Card details are required for card payments.");
            }
            if (cardLeg.amount() != null) {
                cardAmount = cardLeg.amount();
            } else {
                cardAmount = order.total().subtract(nonCardSum);
            }
            if (cardAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.of(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "Computed card amount must be positive.");
            }
        }

        BigDecimal totalSum = nonCardSum.add(cardAmount);
        if (totalSum.compareTo(order.total()) != 0) {
            throw ApiException.of(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    String.format("Payment sum (%s) does not match order total (%s).", totalSum, order.total()));
        }

        // If card leg exists, charge card
        String cardBrand = null;
        String cardLast4 = null;
        String gatewayRef = null;

        if (cardLeg != null) {
            CardDetails cd = cardLeg.card();
            cardBrand = MockPaymentGateway.detectBrand(cd.number());
            cardLast4 = cd.number().substring(cd.number().length() - 4);

            ChargeRequest chargeReq = new ChargeRequest(
                    cardAmount,
                    order.currency(),
                    cd.number(),
                    cd.expiryMonth(),
                    cd.expiryYear(),
                    cd.cvv());

            GatewayResponse res = gateway.authorizeAndCapture(chargeReq);
            if (!res.successful()) {
                repository.insertPayment(
                        order.id(),
                        cardLeg.method(),
                        "FAILED",
                        cardAmount,
                        null,
                        cardBrand,
                        cardLast4,
                        null,
                        res.failureReason(),
                        idempotencyKey,
                        requestHash,
                        now);
                orderService.markPaymentFailed(order.id());
                throw ApiException.of(ErrorCode.PAYMENT_DECLINED, "Payment declined: " + res.failureReason());
            }
            gatewayRef = res.reference();
        }

        // Capture payments
        if (walletLeg != null) {
            UUID pId = repository.insertPayment(
                    order.id(),
                    "WALLET",
                    "CAPTURED",
                    walletAmount,
                    null,
                    null,
                    null,
                    null,
                    null,
                    idempotencyKey,
                    requestHash,
                    now);
            walletService.debitForPayment(userId, walletAmount, order.id(), pId);
        }

        if (pointsLeg != null) {
            UUID pId = repository.insertPayment(
                    order.id(),
                    "GIFT_POINTS",
                    "CAPTURED",
                    pointsAmount,
                    pointsUsed,
                    null,
                    null,
                    null,
                    null,
                    idempotencyKey,
                    requestHash,
                    now);
            giftPointService.redeemForPayment(userId, pointsUsed, order.id(), pId);
        }

        if (cardLeg != null) {
            repository.insertPayment(
                    order.id(),
                    cardLeg.method(),
                    "CAPTURED",
                    cardAmount,
                    null,
                    cardBrand,
                    cardLast4,
                    gatewayRef,
                    null,
                    idempotencyKey,
                    requestHash,
                    now);
        }

        orderService.confirmPaid(order.id());

        Order confirmedOrder = orderService.view(order.id());
        List<Payment> capturedPayments = getPaymentsForOrder(order.id());
        return new PayOrderResponse(new PaymentResult(confirmedOrder, capturedPayments), false);
    }

    @Transactional(readOnly = true)
    public Payment getPayment(UUID paymentId, Caller caller) {
        caller.requireUser();
        PaymentRow row = repository.findById(paymentId)
                .orElseThrow(() -> ApiException.of(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found: " + paymentId));

        if (!caller.isAdmin()) {
            OrderRef orderRef = orderService.findRef(row.orderId())
                    .orElseThrow(() -> ApiException.of(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found: " + paymentId));
            if (!caller.userId().equals(orderRef.userId())) {
                throw ApiException.of(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found: " + paymentId);
            }
        }

        return toDto(row);
    }

    @Transactional
    public RefundResult refundPayment(UUID paymentId, String idempotencyKey, RefundRequest request, Caller caller) {
        if (!caller.isAdmin()) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "Only administrators can issue manual refunds.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.of(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key header is required.");
        }

        String requestHash = hashRefundRequest(request);
        Optional<RefundRow> existingRefund = repository.findRefundByIdempotencyKey(paymentId, idempotencyKey);
        if (existingRefund.isPresent()) {
            RefundRow row = existingRefund.get();
            if (requestHash.equals(row.requestHash())) {
                return new RefundResult(toDto(row), true);
            } else {
                throw ApiException.of(ErrorCode.IDEMPOTENCY_KEY_REUSED, "Idempotency key has already been used with different request parameters.");
            }
        }

        PaymentRow payment = repository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> ApiException.of(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found: " + paymentId));

        if (!"CAPTURED".equals(payment.status()) && !"PARTIALLY_REFUNDED".equals(payment.status())) {
            throw ApiException.of(ErrorCode.PAYMENT_INVALID_STATE, "Payment is not in a refundable status: " + payment.status());
        }

        BigDecimal alreadyRefunded = repository.sumRefundedByPaymentId(paymentId);
        BigDecimal remainingRefundable = payment.amount().subtract(alreadyRefunded);

        if (request.amount().compareTo(remainingRefundable) > 0) {
            throw ApiException.of(ErrorCode.REFUND_EXCEEDS_PAYMENT,
                    String.format("Refund amount (%s) exceeds remaining refundable amount (%s).", request.amount(), remainingRefundable));
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        OrderRef orderRef = orderService.findRef(payment.orderId())
                .orElseThrow(() -> ApiException.of(ErrorCode.PAYMENT_NOT_FOUND, "Order not found for payment."));
        StorePolicy policy = storeService.policy(orderRef.storeId());

        UUID refundId = repository.insertRefund(
                paymentId,
                payment.orderId(),
                null,
                request.amount(),
                "COMPLETED",
                request.reason(),
                idempotencyKey,
                requestHash,
                now);

        executeRefundMethod(payment, orderRef.userId(), request.amount(), policy, refundId);

        BigDecimal newTotalRefunded = alreadyRefunded.add(request.amount());
        String newStatus = newTotalRefunded.compareTo(payment.amount()) >= 0 ? "REFUNDED" : "PARTIALLY_REFUNDED";
        repository.updatePaymentStatus(paymentId, newStatus, null, null);

        RefundRow insertedRefund = repository.findRefundsByPaymentId(paymentId).stream()
                .filter(r -> r.id().equals(refundId))
                .findFirst()
                .orElseThrow();

        return new RefundResult(toDto(insertedRefund), false);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal refundAllForOrder(UUID orderId, UUID returnId, String reason) {
        List<PaymentRow> payments = repository.findByOrderId(orderId);
        OrderRef orderRef = orderService.findRef(orderId)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId));
        StorePolicy policy = storeService.policy(orderRef.storeId());
        OffsetDateTime now = OffsetDateTime.now(clock);
        BigDecimal totalRefundedNow = BigDecimal.ZERO;

        for (PaymentRow p : payments) {
            if ("CAPTURED".equals(p.status()) || "PARTIALLY_REFUNDED".equals(p.status())) {
                BigDecimal alreadyRefunded = repository.sumRefundedByPaymentId(p.id());
                BigDecimal remaining = p.amount().subtract(alreadyRefunded);
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    UUID refundId = repository.insertRefund(
                            p.id(),
                            orderId,
                            returnId,
                            remaining,
                            "COMPLETED",
                            reason,
                            null,
                            null,
                            now);

                    executeRefundMethod(p, orderRef.userId(), remaining, policy, refundId);
                    repository.updatePaymentStatus(p.id(), "REFUNDED", null, null);
                    totalRefundedNow = totalRefundedNow.add(remaining);
                }
            }
        }
        return totalRefundedNow;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> paymentIdsOf(UUID orderId) {
        return repository.findByOrderId(orderId).stream().map(PaymentRow::id).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal refundedTotal(UUID orderId) {
        return repository.sumRefundedByOrderId(orderId);
    }

    private void executeRefundMethod(PaymentRow payment, UUID userId, BigDecimal amount, StorePolicy policy, UUID refundId) {
        String method = payment.method();
        if ("CREDIT_CARD".equals(method) || "DEBIT_CARD".equals(method)) {
            gateway.refund(new PaymentGateway.RefundRequest(payment.gatewayReference(), amount, properties.currency()));
        } else if ("WALLET".equals(method)) {
            walletService.creditForRefund(userId, amount, payment.orderId(), payment.id(), refundId);
        } else if ("GIFT_POINTS".equals(method)) {
            long pointsToRefund = amount.multiply(BigDecimal.valueOf(policy.giftPointsPerCurrencyUnit())).longValue();
            giftPointService.refund(userId, pointsToRefund, payment.orderId(), payment.id(), refundId);
        }
    }

    private List<Payment> getPaymentsForOrder(UUID orderId) {
        return repository.findByOrderId(orderId).stream()
                .filter(p -> !"FAILED".equals(p.status()))
                .map(this::toDto)
                .toList();
    }

    private Payment toDto(PaymentRow row) {
        List<Refund> refunds = repository.findRefundsByPaymentId(row.id()).stream()
                .map(this::toDto)
                .toList();

        return new Payment(
                row.id(),
                row.orderId(),
                row.method(),
                row.status(),
                row.amount(),
                row.pointsUsed(),
                row.cardBrand(),
                row.cardLast4(),
                row.gatewayReference(),
                row.failureReason(),
                row.processedAt(),
                row.createdAt(),
                refunds);
    }

    private Refund toDto(RefundRow row) {
        return new Refund(
                row.id(),
                row.paymentId(),
                row.orderId(),
                row.returnId(),
                row.amount(),
                row.status(),
                row.reason(),
                row.refundedAt(),
                row.createdAt());
    }

    private static String hashPayOrderRequest(PayOrderRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.orderId()).append("|");
        for (PaymentLeg leg : request.legs()) {
            sb.append(leg.method()).append(":")
                    .append(leg.amount() != null ? leg.amount().toPlainString() : "")
                    .append(":")
                    .append(leg.points() != null ? leg.points() : "")
                    .append(":");
            if (leg.card() != null) {
                String cardNum = leg.card().number();
                String last4 = (cardNum != null && cardNum.length() >= 4) ? cardNum.substring(cardNum.length() - 4) : "";
                sb.append(last4).append(":").append(leg.card().expiryMonth()).append(":").append(leg.card().expiryYear());
            }
            sb.append(";");
        }
        return sha256Hex(sb.toString());
    }

    private static String hashRefundRequest(RefundRequest request) {
        String input = (request.amount() != null ? request.amount().toPlainString() : "")
                + "|" + (request.reason() != null ? request.reason().trim() : "");
        return sha256Hex(input);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
