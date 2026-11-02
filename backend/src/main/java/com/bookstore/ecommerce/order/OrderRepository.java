package com.bookstore.ecommerce.order;

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
class OrderRepository {

    record OrderRow(
            UUID id,
            String orderNumber,
            UUID userId,
            UUID storeId,
            UUID cartId,
            String status,
            String currency,
            BigDecimal subtotal,
            BigDecimal taxTotal,
            BigDecimal shippingTotal,
            BigDecimal couponDiscount,
            BigDecimal total,
            UUID couponId,
            String shipFirstName,
            String shipLastName,
            String shipLine1,
            String shipLine2,
            String shipCity,
            String shipState,
            String shipPostalCode,
            String shipCountry,
            String shipEmail,
            String shipPhone,
            OffsetDateTime placedAt,
            OffsetDateTime paymentExpiresAt,
            OffsetDateTime confirmedAt,
            OffsetDateTime cancelDeadlineAt,
            OffsetDateTime cancelledAt,
            String cancellationReason,
            OffsetDateTime deliveredAt,
            OffsetDateTime returnDeadlineAt,
            String idempotencyKey,
            String requestHash,
            long version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    record OrderItemRow(
            UUID id,
            UUID orderId,
            UUID productId,
            String title,
            String isbn,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal,
            BigDecimal taxAmount,
            LocalDate estimatedDeliveryDate,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    record OrderReturnRow(
            UUID id,
            UUID orderId,
            String status,
            String reason,
            OffsetDateTime requestedAt,
            OffsetDateTime resolvedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    record OrderSummaryRow(
            UUID id,
            String orderNumber,
            String status,
            BigDecimal total,
            String currency,
            OffsetDateTime placedAt,
            int itemCount,
            OffsetDateTime cancelDeadlineAt) {
    }

    private static final RowMapper<OrderRow> ORDER_MAPPER = (rs, n) -> new OrderRow(
            rs.getObject("id", UUID.class),
            rs.getString("order_number"),
            rs.getObject("user_id", UUID.class),
            rs.getObject("store_id", UUID.class),
            rs.getObject("cart_id", UUID.class),
            rs.getString("status"),
            rs.getString("currency"),
            rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("tax_total"),
            rs.getBigDecimal("shipping_total"),
            rs.getBigDecimal("coupon_discount"),
            rs.getBigDecimal("total"),
            rs.getObject("coupon_id", UUID.class),
            rs.getString("ship_first_name"),
            rs.getString("ship_last_name"),
            rs.getString("ship_line1"),
            rs.getString("ship_line2"),
            rs.getString("ship_city"),
            rs.getString("ship_state"),
            rs.getString("ship_postal_code"),
            rs.getString("ship_country"),
            rs.getString("ship_email"),
            rs.getString("ship_phone"),
            rs.getObject("placed_at", OffsetDateTime.class),
            rs.getObject("payment_expires_at", OffsetDateTime.class),
            rs.getObject("confirmed_at", OffsetDateTime.class),
            rs.getObject("cancel_deadline_at", OffsetDateTime.class),
            rs.getObject("cancelled_at", OffsetDateTime.class),
            rs.getString("cancellation_reason"),
            rs.getObject("delivered_at", OffsetDateTime.class),
            rs.getObject("return_deadline_at", OffsetDateTime.class),
            rs.getString("idempotency_key"),
            rs.getString("request_hash"),
            rs.getLong("version"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    private static final RowMapper<OrderItemRow> ITEM_MAPPER = (rs, n) -> new OrderItemRow(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getObject("product_id", UUID.class),
            rs.getString("title"),
            rs.getString("isbn"),
            rs.getBigDecimal("unit_price"),
            rs.getInt("quantity"),
            rs.getBigDecimal("line_total"),
            rs.getBigDecimal("tax_amount"),
            rs.getObject("estimated_delivery_date", LocalDate.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    private static final RowMapper<OrderReturnRow> RETURN_MAPPER = (rs, n) -> new OrderReturnRow(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getString("status"),
            rs.getString("reason"),
            rs.getObject("requested_at", OffsetDateTime.class),
            rs.getObject("resolved_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    private final JdbcClient jdbc;

    OrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    String nextOrderNumber() {
        return jdbc.sql("SELECT 'BW-' || to_char(now(), 'YYYYMMDD') || '-' || lpad(nextval('order_number_seq')::text, 6, '0')")
                .query(String.class)
                .single();
    }

    Optional<OrderRow> findByIdempotencyKey(UUID userId, String idempotencyKey) {
        return jdbc.sql("""
                SELECT * FROM orders
                WHERE user_id = :userId AND idempotency_key = :key""")
                .param("userId", userId)
                .param("key", idempotencyKey)
                .query(ORDER_MAPPER)
                .optional();
    }

    Optional<OrderRow> findById(UUID orderId) {
        return jdbc.sql("SELECT * FROM orders WHERE id = :id")
                .param("id", orderId)
                .query(ORDER_MAPPER)
                .optional();
    }

    Optional<OrderRow> findByIdForUpdate(UUID orderId) {
        return jdbc.sql("SELECT * FROM orders WHERE id = :id FOR UPDATE")
                .param("id", orderId)
                .query(ORDER_MAPPER)
                .optional();
    }

    UUID insertOrder(
            String orderNumber,
            UUID userId,
            UUID storeId,
            UUID cartId,
            String status,
            String currency,
            BigDecimal subtotal,
            BigDecimal taxTotal,
            BigDecimal shippingTotal,
            BigDecimal couponDiscount,
            BigDecimal total,
            UUID couponId,
            String shipFirstName,
            String shipLastName,
            String shipLine1,
            String shipLine2,
            String shipCity,
            String shipState,
            String shipPostalCode,
            String shipCountry,
            String shipEmail,
            String shipPhone,
            OffsetDateTime paymentExpiresAt,
            String idempotencyKey,
            String requestHash) {
        return jdbc.sql("""
                INSERT INTO orders (
                    order_number, user_id, store_id, cart_id, status, currency,
                    subtotal, tax_total, shipping_total, coupon_discount, total, coupon_id,
                    ship_first_name, ship_last_name, ship_line1, ship_line2, ship_city, ship_state,
                    ship_postal_code, ship_country, ship_email, ship_phone, payment_expires_at,
                    idempotency_key, request_hash
                ) VALUES (
                    :orderNumber, :userId, :storeId, :cartId, :status, :currency,
                    :subtotal, :taxTotal, :shippingTotal, :couponDiscount, :total, :couponId,
                    :shipFirstName, :shipLastName, :shipLine1, :shipLine2, :shipCity, :shipState,
                    :shipPostalCode, :shipCountry, :shipEmail, :shipPhone, :paymentExpiresAt,
                    :idempotencyKey, :requestHash
                ) RETURNING id""")
                .param("orderNumber", orderNumber)
                .param("userId", userId)
                .param("storeId", storeId)
                .param("cartId", cartId)
                .param("status", status)
                .param("currency", currency)
                .param("subtotal", subtotal)
                .param("taxTotal", taxTotal)
                .param("shippingTotal", shippingTotal)
                .param("couponDiscount", couponDiscount)
                .param("total", total)
                .param("couponId", couponId)
                .param("shipFirstName", shipFirstName)
                .param("shipLastName", shipLastName)
                .param("shipLine1", shipLine1)
                .param("shipLine2", shipLine2)
                .param("shipCity", shipCity)
                .param("shipState", shipState)
                .param("shipPostalCode", shipPostalCode)
                .param("shipCountry", shipCountry)
                .param("shipEmail", shipEmail)
                .param("shipPhone", shipPhone)
                .param("paymentExpiresAt", paymentExpiresAt)
                .param("idempotencyKey", idempotencyKey)
                .param("requestHash", requestHash)
                .query(UUID.class)
                .single();
    }

    void insertOrderItem(
            UUID orderId,
            UUID productId,
            String title,
            String isbn,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal,
            BigDecimal taxAmount,
            LocalDate estimatedDeliveryDate) {
        jdbc.sql("""
                INSERT INTO order_items (
                    order_id, product_id, title, isbn, unit_price, quantity, line_total, tax_amount, estimated_delivery_date
                ) VALUES (
                    :orderId, :productId, :title, :isbn, :unitPrice, :quantity, :lineTotal, :taxAmount, :estimatedDeliveryDate
                )""")
                .param("orderId", orderId)
                .param("productId", productId)
                .param("title", title)
                .param("isbn", isbn)
                .param("unitPrice", unitPrice)
                .param("quantity", quantity)
                .param("lineTotal", lineTotal)
                .param("taxAmount", taxAmount)
                .param("estimatedDeliveryDate", estimatedDeliveryDate)
                .update();
    }

    void insertStatusHistory(UUID orderId, String fromStatus, String toStatus, UUID changedBy, String note) {
        jdbc.sql("""
                INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, note)
                VALUES (:orderId, :fromStatus, :toStatus, :changedBy, :note)""")
                .param("orderId", orderId)
                .param("fromStatus", fromStatus)
                .param("toStatus", toStatus)
                .param("changedBy", changedBy)
                .param("note", note)
                .update();
    }

    void insertCouponRedemption(UUID couponId, UUID orderId, UUID userId) {
        jdbc.sql("""
                INSERT INTO coupon_redemptions (coupon_id, order_id, user_id)
                VALUES (:couponId, :orderId, :userId)""")
                .param("couponId", couponId)
                .param("orderId", orderId)
                .param("userId", userId)
                .update();
    }

    List<OrderItemRow> findItemsByOrderId(UUID orderId) {
        return jdbc.sql("SELECT * FROM order_items WHERE order_id = :orderId ORDER BY created_at ASC")
                .param("orderId", orderId)
                .query(ITEM_MAPPER)
                .list();
    }

    Optional<OrderReturnRow> findReturnByOrderId(UUID orderId) {
        return jdbc.sql("SELECT * FROM order_returns WHERE order_id = :orderId ORDER BY requested_at DESC LIMIT 1")
                .param("orderId", orderId)
                .query(RETURN_MAPPER)
                .optional();
    }

    Optional<UUID> findPendingReturn(UUID orderId) {
        return jdbc.sql("SELECT id FROM order_returns WHERE order_id = :orderId AND status = 'REQUESTED'")
                .param("orderId", orderId)
                .query(UUID.class)
                .optional();
    }

    UUID insertReturnRequest(UUID orderId, String reason) {
        return jdbc.sql("""
                INSERT INTO order_returns (order_id, status, reason)
                VALUES (:orderId, 'REQUESTED', :reason)
                RETURNING id""")
                .param("orderId", orderId)
                .param("reason", reason)
                .query(UUID.class)
                .single();
    }

    void updateReturnStatus(UUID returnId, String status, OffsetDateTime resolvedAt) {
        jdbc.sql("""
                UPDATE order_returns
                SET status = :status, resolved_at = COALESCE(:resolvedAt, resolved_at)
                WHERE id = :id""")
                .param("id", returnId)
                .param("status", status)
                .param("resolvedAt", resolvedAt)
                .update();
    }

    void updateReturnStatusByOrderId(UUID orderId, String status, OffsetDateTime resolvedAt) {
        jdbc.sql("""
                UPDATE order_returns
                SET status = :status, resolved_at = COALESCE(:resolvedAt, resolved_at)
                WHERE order_id = :orderId AND status <> 'REJECTED'""")
                .param("orderId", orderId)
                .param("status", status)
                .param("resolvedAt", resolvedAt)
                .update();
    }

    void updateOrderStatus(UUID orderId, String status) {
        jdbc.sql("UPDATE orders SET status = :status WHERE id = :id")
                .param("id", orderId)
                .param("status", status)
                .update();
    }

    void confirmOrder(UUID orderId, OffsetDateTime confirmedAt, OffsetDateTime cancelDeadlineAt) {
        jdbc.sql("""
                UPDATE orders
                SET status = 'CONFIRMED',
                    confirmed_at = :confirmedAt,
                    cancel_deadline_at = :cancelDeadlineAt
                WHERE id = :id""")
                .param("id", orderId)
                .param("confirmedAt", confirmedAt)
                .param("cancelDeadlineAt", cancelDeadlineAt)
                .update();
    }

    void cancelOrder(UUID orderId, OffsetDateTime cancelledAt, String reason) {
        jdbc.sql("""
                UPDATE orders
                SET status = 'CANCELLED',
                    cancelled_at = :cancelledAt,
                    cancellation_reason = :reason
                WHERE id = :id""")
                .param("id", orderId)
                .param("cancelledAt", cancelledAt)
                .param("reason", reason)
                .update();
    }

    void deliverOrder(UUID orderId, OffsetDateTime deliveredAt, OffsetDateTime returnDeadlineAt) {
        jdbc.sql("""
                UPDATE orders
                SET status = 'DELIVERED',
                    delivered_at = :deliveredAt,
                    return_deadline_at = :returnDeadlineAt
                WHERE id = :id""")
                .param("id", orderId)
                .param("deliveredAt", deliveredAt)
                .param("returnDeadlineAt", returnDeadlineAt)
                .update();
    }

    List<OrderSummaryRow> listUserOrders(UUID userId, String status, int offset, int limit, String sortDir) {
        String sql = """
                SELECT o.id, o.order_number, o.status, o.total, o.currency, o.placed_at,
                       COALESCE(SUM(i.quantity), 0)::integer AS item_count, o.cancel_deadline_at
                FROM orders o
                LEFT JOIN order_items i ON i.order_id = o.id
                WHERE o.user_id = :userId
                """ + (status != null ? " AND o.status = :status " : "")
                + " GROUP BY o.id, o.order_number, o.status, o.total, o.currency, o.placed_at, o.cancel_deadline_at "
                + " ORDER BY o.placed_at " + ("asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC")
                + " LIMIT :limit OFFSET :offset";

        var query = jdbc.sql(sql)
                .param("userId", userId)
                .param("limit", limit)
                .param("offset", offset);
        if (status != null) {
            query = query.param("status", status);
        }

        return query.query((rs, n) -> new OrderSummaryRow(
                rs.getObject("id", UUID.class),
                rs.getString("order_number"),
                rs.getString("status"),
                rs.getBigDecimal("total"),
                rs.getString("currency"),
                rs.getObject("placed_at", OffsetDateTime.class),
                rs.getInt("item_count"),
                rs.getObject("cancel_deadline_at", OffsetDateTime.class)))
                .list();
    }

    int countUserOrders(UUID userId, String status) {
        String sql = "SELECT COUNT(*) FROM orders WHERE user_id = :userId"
                + (status != null ? " AND status = :status" : "");
        var query = jdbc.sql(sql).param("userId", userId);
        if (status != null) {
            query = query.param("status", status);
        }
        return query.query(Integer.class).single();
    }
}
