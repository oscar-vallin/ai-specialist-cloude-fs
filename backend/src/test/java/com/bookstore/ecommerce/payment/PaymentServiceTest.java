package com.bookstore.ecommerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.Caller;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.config.AppProperties;
import com.bookstore.ecommerce.member.GiftPointService;
import com.bookstore.ecommerce.member.WalletService;
import com.bookstore.ecommerce.order.OrderService;
import com.bookstore.ecommerce.order.OrderService.PayableOrder;
import com.bookstore.ecommerce.payment.PaymentDtos.CardDetails;
import com.bookstore.ecommerce.payment.PaymentDtos.PayOrderRequest;
import com.bookstore.ecommerce.payment.PaymentDtos.PaymentLeg;
import com.bookstore.ecommerce.payment.PaymentGateway.GatewayResponse;
import com.bookstore.ecommerce.store.StoreService;
import com.bookstore.ecommerce.store.StoreService.StorePolicy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    private PaymentRepository repository;
    private PaymentGateway gateway;
    private OrderService orderService;
    private WalletService walletService;
    private GiftPointService giftPointService;
    private StoreService storeService;
    private AppProperties properties;
    private Clock clock;
    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(PaymentRepository.class);
        gateway = mock(PaymentGateway.class);
        orderService = mock(OrderService.class);
        walletService = mock(WalletService.class);
        giftPointService = mock(GiftPointService.class);
        storeService = mock(StoreService.class);
        properties = new AppProperties("USD", 100, "Main Store", "US",
                new AppProperties.Order(30),
                new AppProperties.Jwt("test-secret", 60),
                new AppProperties.Payment("4000000000000002"));
        clock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC);
        service = new PaymentServiceImpl(repository, gateway, orderService, walletService, giftPointService, storeService, properties, clock);
    }

    @Test
    void luhnValidationWorks() {
        assertThat(MockPaymentGateway.passesLuhn("4242424242424242")).isTrue();
        assertThat(MockPaymentGateway.passesLuhn("4000000000000002")).isTrue();
        assertThat(MockPaymentGateway.passesLuhn("1234567812345670")).isTrue();
        assertThat(MockPaymentGateway.passesLuhn("4242424242424241")).isFalse();
    }

    @Test
    void payOrderDeclinedCardMarksOrderFailedAndThrows422() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        Caller caller = new Caller(userId, Set.of("CUSTOMER"));

        PayableOrder order = new PayableOrder(orderId, userId, storeId, "PENDING_PAYMENT",
                BigDecimal.valueOf(25.00), "USD", OffsetDateTime.now(clock).plusMinutes(30));

        when(orderService.lockForPayment(orderId, userId)).thenReturn(order);
        when(storeService.policy(storeId)).thenReturn(new StorePolicy(storeId, 48, 7, BigDecimal.valueOf(50.00),
                BigDecimal.valueOf(0.08), 100, 100));

        when(gateway.authorizeAndCapture(any())).thenReturn(new GatewayResponse(false, null, "Card declined by issuer"));

        CardDetails card = new CardDetails("4000000000000002", 12, 2030, "123");
        PaymentLeg leg = new PaymentLeg("CREDIT_CARD", BigDecimal.valueOf(25.00), null, card);
        PayOrderRequest request = new PayOrderRequest(orderId, List.of(leg));

        assertThatThrownBy(() -> service.payOrder(caller, "idemp-key-1", request))
                .isInstanceOf(ApiException.class)
                .matches(e -> ((ApiException) e).code() == ErrorCode.PAYMENT_DECLINED);

        verify(orderService).markPaymentFailed(orderId);
        verify(orderService, never()).confirmPaid(orderId);
    }

    @Test
    void payOrderCombinesGiftPointsAndWalletAndCard() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        Caller caller = new Caller(userId, Set.of("CUSTOMER"));

        // Order total is 30.00: 500 points = 5.00, wallet = 10.00, card = remaining 15.00
        PayableOrder order = new PayableOrder(orderId, userId, storeId, "PENDING_PAYMENT",
                BigDecimal.valueOf(30.00), "USD", OffsetDateTime.now(clock).plusMinutes(30));

        when(orderService.lockForPayment(orderId, userId)).thenReturn(order);
        when(storeService.policy(storeId)).thenReturn(new StorePolicy(storeId, 48, 7, BigDecimal.valueOf(50.00),
                BigDecimal.valueOf(0.08), 100, 100));

        when(giftPointService.pointsBalance(userId)).thenReturn(1000L);
        when(walletService.balance(userId)).thenReturn(BigDecimal.valueOf(50.00));
        when(gateway.authorizeAndCapture(any())).thenReturn(new GatewayResponse(true, "mock_ch_123456", null));

        CardDetails card = new CardDetails("4242424242424242", 12, 2030, "123");
        PaymentLeg pointsLeg = new PaymentLeg("GIFT_POINTS", null, 500L, null);
        PaymentLeg walletLeg = new PaymentLeg("WALLET", BigDecimal.valueOf(10.00), null, null);
        PaymentLeg cardLeg = new PaymentLeg("CREDIT_CARD", null, null, card); // remaining balance

        PayOrderRequest request = new PayOrderRequest(orderId, List.of(pointsLeg, walletLeg, cardLeg));

        service.payOrder(caller, "idemp-combo-1", request);

        verify(giftPointService).redeemForPayment(eq(userId), eq(500L), eq(orderId), any());
        verify(walletService).debitForPayment(eq(userId), eq(BigDecimal.valueOf(10.00)), eq(orderId), any());
        verify(orderService).confirmPaid(orderId);
    }
}
