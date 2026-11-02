package com.bookstore.ecommerce.shipping;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.Caller;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.shipping.ShippingDtos.Shipment;
import com.bookstore.ecommerce.shipping.ShippingDtos.UpdateShipmentStatusRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipments")
@Validated
class ShipmentController {

    private final ShipmentServiceImpl shipmentService;

    ShipmentController(ShipmentServiceImpl shipmentService) {
        this.shipmentService = shipmentService;
    }

    @GetMapping("/{shipmentId}")
    public Shipment getShipment(@PathVariable UUID shipmentId, Caller caller) {
        if (caller.isGuest()) {
            throw ApiException.of(ErrorCode.UNAUTHENTICATED, "Authentication required to view shipments.");
        }
        return shipmentService.getShipment(shipmentId, caller);
    }

    @PatchMapping("/{shipmentId}")
    public Shipment updateShipmentStatus(
            @PathVariable UUID shipmentId,
            @Valid @RequestBody UpdateShipmentStatusRequest request,
            Caller caller) {
        if (caller.isGuest()) {
            throw ApiException.of(ErrorCode.UNAUTHENTICATED, "Authentication required.");
        }
        if (!caller.isAdmin()) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "Only administrators can update shipment status.");
        }
        return shipmentService.updateShipmentStatus(shipmentId, request);
    }

    @PostMapping("/{shipmentId}/return")
    public ResponseEntity<Shipment> createReturnShipment(@PathVariable UUID shipmentId, Caller caller) {
        if (caller.isGuest()) {
            throw ApiException.of(ErrorCode.UNAUTHENTICATED, "Authentication required.");
        }
        if (!caller.isAdmin()) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "Only administrators can approve returns.");
        }
        Shipment returnShipment = shipmentService.createReturnShipment(shipmentId);
        return ResponseEntity.created(URI.create("/api/v1/shipments/" + returnShipment.id()))
                .body(returnShipment);
    }
}
