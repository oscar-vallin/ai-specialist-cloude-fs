package com.bookstore.ecommerce.shipping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What the order domain may ask the shipping domain. Write methods join the caller's transaction. */
public interface ShipmentService {

    /** Order confirmed: creates the outbound shipment in status {@code CREATED}. */
    void createOutbound(UUID orderId, BigDecimal shippingCost, LocalDate estimatedDeliveryDate);

    /** Order cancelled: closes the open outbound shipment as {@code FAILED}. */
    void closeOutbound(UUID orderId);

    /** Outbound shipment first, then the return shipment if any. */
    List<ShipmentRef> refsOfOrder(UUID orderId);

    record ShipmentRef(UUID id, String type, String status) {
    }
}
