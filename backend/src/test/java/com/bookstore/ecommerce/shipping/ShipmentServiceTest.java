package com.bookstore.ecommerce.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.Caller;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.order.OrderService;
import com.bookstore.ecommerce.shipping.ShipmentRepository.ShipmentRow;
import com.bookstore.ecommerce.shipping.ShippingDtos.Shipment;
import com.bookstore.ecommerce.shipping.ShippingDtos.UpdateShipmentStatusRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShipmentServiceTest {

    private ShipmentRepository repository;
    private OrderService orderService;
    private ShipmentServiceImpl service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        repository = mock(ShipmentRepository.class);
        orderService = mock(OrderService.class);
        clock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC);
        service = new ShipmentServiceImpl(repository, orderService, clock);
    }

    @Test
    void getShipmentAllowsOwnerAndAdmin() {
        UUID shipmentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ShipmentRow row = new ShipmentRow(
                shipmentId, orderId, "OUTBOUND", null, "CREATED", null, null,
                BigDecimal.valueOf(5.00), LocalDate.now(clock), null, null, OffsetDateTime.now(clock));

        when(repository.findById(shipmentId)).thenReturn(Optional.of(row));
        when(orderService.findRef(orderId)).thenReturn(Optional.of(new OrderService.OrderRef(orderId, userId, UUID.randomUUID())));

        // Owner gets it
        Caller owner = new Caller(userId, Set.of("CUSTOMER"));
        Shipment dto = service.getShipment(shipmentId, owner);
        assertThat(dto.id()).isEqualTo(shipmentId);

        // Admin gets it
        Caller admin = new Caller(UUID.randomUUID(), Set.of("ADMIN"));
        Shipment adminDto = service.getShipment(shipmentId, admin);
        assertThat(adminDto.id()).isEqualTo(shipmentId);

        // Stranger gets 404
        Caller stranger = new Caller(UUID.randomUUID(), Set.of("CUSTOMER"));
        assertThatThrownBy(() -> service.getShipment(shipmentId, stranger))
                .isInstanceOf(ApiException.class)
                .matches(e -> ((ApiException) e).code() == ErrorCode.SHIPMENT_NOT_FOUND);
    }

    @Test
    void outboundShipmentTransitionToInTransitRequiresCarrierAndTracking() {
        UUID shipmentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        ShipmentRow row = new ShipmentRow(
                shipmentId, orderId, "OUTBOUND", null, "CREATED", null, null,
                BigDecimal.valueOf(5.00), LocalDate.now(clock), null, null, OffsetDateTime.now(clock));

        when(repository.findById(shipmentId)).thenReturn(Optional.of(row));

        // Missing carrier or tracking number throws VALIDATION_FAILED
        assertThatThrownBy(() -> service.updateShipmentStatus(shipmentId, new UpdateShipmentStatusRequest("IN_TRANSIT", "", "")))
                .isInstanceOf(ApiException.class)
                .matches(e -> ((ApiException) e).code() == ErrorCode.VALIDATION_FAILED);

        // Valid request transitions and marks order SHIPPED
        ShipmentRow updatedRow = new ShipmentRow(
                shipmentId, orderId, "OUTBOUND", null, "IN_TRANSIT", "DHL", "TRACK123",
                BigDecimal.valueOf(5.00), LocalDate.now(clock), OffsetDateTime.now(clock), null, OffsetDateTime.now(clock));
        when(repository.findById(shipmentId)).thenReturn(Optional.of(row), Optional.of(updatedRow));

        Shipment result = service.updateShipmentStatus(shipmentId, new UpdateShipmentStatusRequest("IN_TRANSIT", "DHL", "TRACK123"));
        assertThat(result.status()).isEqualTo("IN_TRANSIT");
        verify(orderService).markShipped(orderId);
    }

    @Test
    void outboundShipmentTransitionToDeliveredMarksOrderDelivered() {
        UUID shipmentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        ShipmentRow row = new ShipmentRow(
                shipmentId, orderId, "OUTBOUND", null, "IN_TRANSIT", "DHL", "TRACK123",
                BigDecimal.valueOf(5.00), LocalDate.now(clock), OffsetDateTime.now(clock), null, OffsetDateTime.now(clock));
        ShipmentRow deliveredRow = new ShipmentRow(
                shipmentId, orderId, "OUTBOUND", null, "DELIVERED", "DHL", "TRACK123",
                BigDecimal.valueOf(5.00), LocalDate.now(clock), OffsetDateTime.now(clock), OffsetDateTime.now(clock), OffsetDateTime.now(clock));

        when(repository.findById(shipmentId)).thenReturn(Optional.of(row), Optional.of(deliveredRow));

        Shipment result = service.updateShipmentStatus(shipmentId, new UpdateShipmentStatusRequest("DELIVERED", null, null));
        assertThat(result.status()).isEqualTo("DELIVERED");
        verify(orderService).markDelivered(orderId);
    }
}
