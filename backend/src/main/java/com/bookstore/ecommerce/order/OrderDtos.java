package com.bookstore.ecommerce.order;

import com.bookstore.ecommerce.member.MemberDtos.Address;
import com.bookstore.ecommerce.shipping.ShipmentService.ShipmentRef;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Request and response bodies of the cart and order domain (see api/openapi.yaml, tags Cart and Orders). */
public final class OrderDtos {

    private OrderDtos() {
    }

    // ----- Cart -----

    public record AddCartItemRequest(@NotNull UUID productId, @NotNull @Min(1) @Max(99) Integer quantity) {
    }

    public record UpdateCartItemRequest(@NotNull @Min(1) @Max(99) Integer quantity) {
    }

    public record ApplyCouponRequest(@NotBlank @Size(max = 50) String code) {
    }

    public record CartItem(UUID id, UUID productId, String title, String coverFrontUrl, BigDecimal unitPrice,
            int quantity, BigDecimal lineTotal, boolean inStock) {
    }

    public record Cart(UUID id, UUID storeId, String status, String guestToken, List<CartItem> items, int itemCount,
            BigDecimal subtotal, String couponCode, String currency) {
    }

    // ----- Checkout and orders -----

    public record CheckoutRequest(@NotNull UUID addressId, @Size(max = 50) String couponCode) {
    }

    public record PlaceOrderRequest(@NotNull UUID addressId, @Size(max = 50) String couponCode,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal expectedTotal) {
    }

    public record CancelOrderRequest(@Size(max = 500) String reason) {
    }

    public record ReturnOrderRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record QuoteItem(UUID productId, String title, BigDecimal unitPrice, int quantity, BigDecimal lineTotal,
            BigDecimal taxAmount, LocalDate estimatedDeliveryDate) {
    }

    public record CheckoutQuote(String currency, List<QuoteItem> items, BigDecimal subtotal, BigDecimal taxTotal,
            BigDecimal shippingTotal, BigDecimal couponDiscount, BigDecimal total, String couponCode,
            Address shippingAddress, LocalDate estimatedDeliveryDate, long giftPointsBalance,
            long maxGiftPointsPayable, OffsetDateTime quotedAt) {
    }

    public record ShippingAddress(String firstName, String lastName, String line1, String line2, String city,
            String state, String postalCode, String country, String email, String phone) {
    }

    public record OrderItem(UUID id, UUID productId, String title, String isbn, BigDecimal unitPrice, int quantity,
            BigDecimal lineTotal, BigDecimal taxAmount, LocalDate estimatedDeliveryDate) {
    }

    public record OrderReturn(UUID id, String status, String reason, OffsetDateTime requestedAt,
            OffsetDateTime resolvedAt) {
    }

    public record Order(UUID id, String orderNumber, String status, String currency, List<OrderItem> items,
            BigDecimal subtotal, BigDecimal taxTotal, BigDecimal shippingTotal, BigDecimal couponDiscount,
            BigDecimal total, BigDecimal refundedTotal, String couponCode, ShippingAddress shippingAddress,
            OffsetDateTime placedAt, OffsetDateTime paymentExpiresAt, OffsetDateTime confirmedAt,
            OffsetDateTime cancelDeadlineAt, OffsetDateTime cancelledAt, String cancellationReason,
            OffsetDateTime deliveredAt, OffsetDateTime returnDeadlineAt, OrderReturn returnRequest,
            List<UUID> paymentIds, List<ShipmentRef> shipments) {
    }

    public record OrderSummary(UUID id, String orderNumber, String status, BigDecimal total, String currency,
            OffsetDateTime placedAt, int itemCount, OffsetDateTime cancelDeadlineAt) {
    }

    public record BuyAgainLine(UUID productId, String title, int requestedQuantity, int addedQuantity,
            String outcome, String reason, BigDecimal previousUnitPrice, BigDecimal currentUnitPrice) {
    }

    public record BuyAgainResult(List<BuyAgainLine> lines, Cart cart) {
    }
}
