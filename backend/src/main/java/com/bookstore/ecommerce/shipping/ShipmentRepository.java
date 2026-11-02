package com.bookstore.ecommerce.shipping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ShipmentRepository {

    record ShipmentRow(
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

    private static final RowMapper<ShipmentRow> SHIPMENT_MAPPER = (rs, n) -> new ShipmentRow(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getString("type"),
            rs.getObject("return_id", UUID.class),
            rs.getString("status"),
            rs.getString("carrier"),
            rs.getString("tracking_number"),
            rs.getBigDecimal("shipping_cost"),
            rs.getObject("estimated_delivery_date", LocalDate.class),
            rs.getObject("shipped_at", OffsetDateTime.class),
            rs.getObject("delivered_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class));

    private final JdbcClient jdbc;

    ShipmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    UUID insertOutbound(UUID orderId, BigDecimal shippingCost, LocalDate estimatedDeliveryDate) {
        return jdbc.sql("""
                INSERT INTO shipments (order_id, type, status, shipping_cost, estimated_delivery_date)
                VALUES (:orderId, 'OUTBOUND', 'CREATED', :shippingCost, :estimatedDeliveryDate)
                RETURNING id""")
                .param("orderId", orderId)
                .param("shippingCost", shippingCost)
                .param("estimatedDeliveryDate", estimatedDeliveryDate)
                .query(UUID.class)
                .single();
    }

    UUID insertReturn(UUID orderId, UUID returnId) {
        return jdbc.sql("""
                INSERT INTO shipments (order_id, type, return_id, status, shipping_cost)
                VALUES (:orderId, 'RETURN', :returnId, 'CREATED', 0.00)
                RETURNING id""")
                .param("orderId", orderId)
                .param("returnId", returnId)
                .query(UUID.class)
                .single();
    }

    Optional<ShipmentRow> findById(UUID id) {
        return jdbc.sql("""
                SELECT id, order_id, type, return_id, status, carrier, tracking_number,
                       shipping_cost, estimated_delivery_date, shipped_at, delivered_at, created_at
                FROM shipments
                WHERE id = :id""")
                .param("id", id)
                .query(SHIPMENT_MAPPER)
                .optional();
    }

    List<ShipmentRow> findByOrderId(UUID orderId) {
        return jdbc.sql("""
                SELECT id, order_id, type, return_id, status, carrier, tracking_number,
                       shipping_cost, estimated_delivery_date, shipped_at, delivered_at, created_at
                FROM shipments
                WHERE order_id = :orderId
                ORDER BY CASE WHEN type = 'OUTBOUND' THEN 0 ELSE 1 END, created_at ASC""")
                .param("orderId", orderId)
                .query(SHIPMENT_MAPPER)
                .list();
    }

    boolean hasReturnShipment(UUID returnId) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM shipments WHERE return_id = :returnId)")
                .param("returnId", returnId)
                .query(Boolean.class)
                .single();
    }

    void updateStatus(UUID id, String status, String carrier, String trackingNumber,
                      OffsetDateTime shippedAt, OffsetDateTime deliveredAt) {
        jdbc.sql("""
                UPDATE shipments
                SET status = :status,
                    carrier = COALESCE(:carrier, carrier),
                    tracking_number = COALESCE(:trackingNumber, tracking_number),
                    shipped_at = COALESCE(:shippedAt, shipped_at),
                    delivered_at = COALESCE(:deliveredAt, delivered_at)
                WHERE id = :id""")
                .param("id", id)
                .param("status", status)
                .param("carrier", carrier)
                .param("trackingNumber", trackingNumber)
                .param("shippedAt", shippedAt)
                .param("deliveredAt", deliveredAt)
                .update();
    }

    void closeOutbound(UUID orderId) {
        jdbc.sql("""
                UPDATE shipments
                SET status = 'FAILED'
                WHERE order_id = :orderId AND type = 'OUTBOUND' AND status NOT IN ('DELIVERED', 'FAILED')""")
                .param("orderId", orderId)
                .update();
    }
}
