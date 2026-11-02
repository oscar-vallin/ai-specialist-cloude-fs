package com.bookstore.ecommerce.shipping;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** DTOs of the Shipping domain matching api/openapi.yaml. */
public final class ShippingDtos {

    private ShippingDtos() {
    }

    public record ShippingRateQuote(
            String country,
            int weightGrams,
            String currency,
            BigDecimal baseRate,
            BigDecimal rate,
            boolean freeShippingApplied,
            BigDecimal freeShippingThreshold,
            int estimatedDaysMin,
            int estimatedDaysMax) {
    }

    public record DeliveryEstimate(
            UUID productId,
            String country,
            boolean shippable,
            Integer estimatedDaysMin,
            Integer estimatedDaysMax,
            LocalDate tentativeDeliveryDate) {
    }

    public record ShipmentRef(UUID id, String type, String status) {
    }

    public record Shipment(
            UUID id,
            UUID orderId,
            String type,
            UUID returnId,
            String status,
            String carrier,
            String trackingNumber,
            BigDecimal shippingCost,
            LocalDate estimatedDeliveryDate,
            OffsetDateTime shippedAt,
            OffsetDateTime deliveredAt,
            OffsetDateTime createdAt) {
    }

    public record UpdateShipmentStatusRequest(
            @NotNull String status,
            @Size(max = 100) String carrier,
            @Size(max = 100) String trackingNumber) {
    }
}
