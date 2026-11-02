package com.bookstore.ecommerce.shipping;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.Caller;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.order.OrderService;
import com.bookstore.ecommerce.shipping.ShipmentRepository.ShipmentRow;
import com.bookstore.ecommerce.shipping.ShippingDtos.Shipment;
import com.bookstore.ecommerce.shipping.ShippingDtos.UpdateShipmentStatusRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class ShipmentServiceImpl implements ShipmentService {

    private final ShipmentRepository repository;
    private final OrderService orderService;
    private final Clock clock;

    ShipmentServiceImpl(ShipmentRepository repository, @org.springframework.context.annotation.Lazy OrderService orderService, Clock clock) {
        this.repository = repository;
        this.orderService = orderService;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void createOutbound(UUID orderId, BigDecimal shippingCost, LocalDate estimatedDeliveryDate) {
        repository.insertOutbound(orderId, shippingCost != null ? shippingCost : BigDecimal.ZERO, estimatedDeliveryDate);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeOutbound(UUID orderId) {
        repository.closeOutbound(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShipmentRef> refsOfOrder(UUID orderId) {
        return repository.findByOrderId(orderId).stream()
                .map(row -> new ShipmentRef(row.id(), row.type(), row.status()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Shipment getShipment(UUID shipmentId, Caller caller) {
        ShipmentRow row = repository.findById(shipmentId)
                .orElseThrow(() -> ApiException.of(ErrorCode.SHIPMENT_NOT_FOUND, "Shipment not found: " + shipmentId));

        if (!caller.isAdmin()) {
            var orderRef = orderService.findRef(row.orderId())
                    .orElseThrow(() -> ApiException.of(ErrorCode.SHIPMENT_NOT_FOUND, "Shipment not found: " + shipmentId));
            if (caller.userId() == null || !caller.userId().equals(orderRef.userId())) {
                throw ApiException.of(ErrorCode.SHIPMENT_NOT_FOUND, "Shipment not found: " + shipmentId);
            }
        }

        return toDto(row);
    }

    @Transactional
    public Shipment updateShipmentStatus(UUID shipmentId, UpdateShipmentStatusRequest request) {
        ShipmentRow row = repository.findById(shipmentId)
                .orElseThrow(() -> ApiException.of(ErrorCode.SHIPMENT_NOT_FOUND, "Shipment not found: " + shipmentId));

        String from = row.status();
        String to = request.status();
        String type = row.type();
        OffsetDateTime now = OffsetDateTime.now(clock);

        if ("OUTBOUND".equals(type)) {
            if ("CREATED".equals(from) && "IN_TRANSIT".equals(to)) {
                if (request.carrier() == null || request.carrier().isBlank()
                        || request.trackingNumber() == null || request.trackingNumber().isBlank()) {
                    throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Carrier and tracking number are required when shipping an outbound parcel.");
                }
                repository.updateStatus(shipmentId, to, request.carrier(), request.trackingNumber(), now, null);
                orderService.markShipped(row.orderId());
            } else if ("IN_TRANSIT".equals(from) && "DELIVERED".equals(to)) {
                repository.updateStatus(shipmentId, to, request.carrier(), request.trackingNumber(), null, now);
                orderService.markDelivered(row.orderId());
            } else if (("CREATED".equals(from) || "IN_TRANSIT".equals(from)) && "FAILED".equals(to)) {
                repository.updateStatus(shipmentId, to, request.carrier(), request.trackingNumber(), null, null);
            } else {
                throw ApiException.of(ErrorCode.SHIPMENT_INVALID_STATE,
                        String.format("Invalid status transition for outbound shipment from %s to %s", from, to));
            }
        } else if ("RETURN".equals(type)) {
            if ("CREATED".equals(from) && "IN_TRANSIT".equals(to)) {
                repository.updateStatus(shipmentId, to, request.carrier(), request.trackingNumber(), now, null);
            } else if ("IN_TRANSIT".equals(from) && "DELIVERED".equals(to)) {
                repository.updateStatus(shipmentId, to, request.carrier(), request.trackingNumber(), null, now);
                orderService.completeReturn(row.orderId());
            } else if (("CREATED".equals(from) || "IN_TRANSIT".equals(from)) && "FAILED".equals(to)) {
                repository.updateStatus(shipmentId, to, request.carrier(), request.trackingNumber(), null, null);
            } else {
                throw ApiException.of(ErrorCode.SHIPMENT_INVALID_STATE,
                        String.format("Invalid status transition for return shipment from %s to %s", from, to));
            }
        } else {
            throw ApiException.of(ErrorCode.SHIPMENT_INVALID_STATE, "Unknown shipment type: " + type);
        }

        return toDto(repository.findById(shipmentId).orElseThrow());
    }

    @Transactional
    public Shipment createReturnShipment(UUID outboundShipmentId) {
        ShipmentRow outbound = repository.findById(outboundShipmentId)
                .orElseThrow(() -> ApiException.of(ErrorCode.SHIPMENT_NOT_FOUND, "Shipment not found: " + outboundShipmentId));

        if (!"OUTBOUND".equals(outbound.type()) || !"DELIVERED".equals(outbound.status())) {
            throw ApiException.of(ErrorCode.SHIPMENT_INVALID_STATE, "Return shipment can only be created for delivered outbound shipments.");
        }

        UUID returnId = orderService.pendingReturn(outbound.orderId())
                .orElseThrow(() -> ApiException.of(ErrorCode.SHIPMENT_INVALID_STATE, "No open return request for order: " + outbound.orderId()));

        if (repository.hasReturnShipment(returnId)) {
            throw ApiException.of(ErrorCode.SHIPMENT_INVALID_STATE, "A return shipment already exists for this return request.");
        }

        UUID returnShipmentId = repository.insertReturn(outbound.orderId(), returnId);
        orderService.approveReturn(returnId);

        return toDto(repository.findById(returnShipmentId).orElseThrow());
    }

    private Shipment toDto(ShipmentRow row) {
        return new Shipment(
                row.id(),
                row.orderId(),
                row.type(),
                row.returnId(),
                row.status(),
                row.carrier(),
                row.trackingNumber(),
                row.shippingCost(),
                row.estimatedDeliveryDate(),
                row.shippedAt(),
                row.deliveredAt(),
                row.createdAt());
    }
}
