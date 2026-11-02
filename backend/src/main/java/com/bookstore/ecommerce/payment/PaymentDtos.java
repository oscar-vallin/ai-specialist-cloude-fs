package com.bookstore.ecommerce.payment;

import com.bookstore.ecommerce.order.OrderDtos.Order;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs of the Payment domain matching api/openapi.yaml. */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record CardDetails(
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            @NotBlank
            @Pattern(regexp = "^\\d{13,19}$")
            String number,

            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            @NotNull
            @Min(1)
            @Max(12)
            Integer expiryMonth,

            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            @NotNull
            @Min(2000)
            @Max(2100)
            Integer expiryYear,

            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            @NotBlank
            @Pattern(regexp = "^\\d{3,4}$")
            String cvv) {
    }

    public record PaymentLeg(
            @NotNull
            @Pattern(regexp = "^(CREDIT_CARD|DEBIT_CARD|WALLET|GIFT_POINTS)$")
            String method,

            @DecimalMin("0.01")
            @Digits(integer = 10, fraction = 2)
            BigDecimal amount,

            @Min(1)
            Long points,

            @Valid
            CardDetails card) {
    }

    public record PayOrderRequest(
            @NotNull
            UUID orderId,

            @NotNull
            @Size(min = 1, max = 3)
            List<@Valid PaymentLeg> legs) {
    }

    public record Refund(
            UUID id,
            UUID paymentId,
            UUID orderId,
            UUID returnId,
            BigDecimal amount,
            String status,
            String reason,
            OffsetDateTime refundedAt,
            OffsetDateTime createdAt) {
    }

    public record Payment(
            UUID id,
            UUID orderId,
            String method,
            String status,
            BigDecimal amount,
            Long pointsUsed,
            String cardBrand,
            String cardLast4,
            String gatewayReference,
            String failureReason,
            OffsetDateTime processedAt,
            OffsetDateTime createdAt,
            List<Refund> refunds) {
    }

    public record PaymentResult(
            Order order,
            List<Payment> payments) {
    }

    public record RefundRequest(
            @NotNull
            @DecimalMin("0.01")
            @Digits(integer = 10, fraction = 2)
            BigDecimal amount,

            @NotBlank
            @Size(max = 500)
            String reason) {
    }
}
