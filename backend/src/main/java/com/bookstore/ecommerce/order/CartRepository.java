package com.bookstore.ecommerce.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CartRepository {

    record CartRow(UUID id, UUID storeId, UUID userId, String guestToken, String status, UUID mergedIntoCartId,
            UUID couponId, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    record CartItemRow(UUID id, UUID cartId, UUID productId, int quantity, OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    record CouponRow(UUID id, UUID storeId, String code, String discountType, BigDecimal discountValue,
            BigDecimal minOrderAmount, Integer maxRedemptions, OffsetDateTime validFrom, OffsetDateTime validUntil,
            boolean active) {
    }

    private static final RowMapper<CartRow> CART_MAPPER = (rs, n) -> new CartRow(
            rs.getObject("id", UUID.class),
            rs.getObject("store_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("guest_token"),
            rs.getString("status"),
            rs.getObject("merged_into_cart_id", UUID.class),
            rs.getObject("coupon_id", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    private static final RowMapper<CartItemRow> ITEM_MAPPER = (rs, n) -> new CartItemRow(
            rs.getObject("id", UUID.class),
            rs.getObject("cart_id", UUID.class),
            rs.getObject("product_id", UUID.class),
            rs.getInt("quantity"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    private static final RowMapper<CouponRow> COUPON_MAPPER = (rs, n) -> new CouponRow(
            rs.getObject("id", UUID.class),
            rs.getObject("store_id", UUID.class),
            rs.getString("code"),
            rs.getString("discount_type"),
            rs.getBigDecimal("discount_value"),
            rs.getBigDecimal("min_order_amount"),
            rs.getObject("max_redemptions", Integer.class),
            rs.getObject("valid_from", OffsetDateTime.class),
            rs.getObject("valid_until", OffsetDateTime.class),
            rs.getBoolean("active"));

    private final JdbcClient jdbc;

    CartRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<CartRow> findActiveByUser(UUID userId, UUID storeId) {
        return jdbc.sql("""
                SELECT id, store_id, user_id, guest_token, status, merged_into_cart_id, coupon_id, created_at, updated_at
                FROM carts
                WHERE user_id = :userId AND store_id = :storeId AND status = 'ACTIVE'""")
                .param("userId", userId)
                .param("storeId", storeId)
                .query(CART_MAPPER)
                .optional();
    }

    Optional<CartRow> findActiveByGuestToken(String guestToken) {
        return jdbc.sql("""
                SELECT id, store_id, user_id, guest_token, status, merged_into_cart_id, coupon_id, created_at, updated_at
                FROM carts
                WHERE guest_token = :token AND status = 'ACTIVE'""")
                .param("token", guestToken)
                .query(CART_MAPPER)
                .optional();
    }

    Optional<CartRow> findById(UUID cartId) {
        return jdbc.sql("""
                SELECT id, store_id, user_id, guest_token, status, merged_into_cart_id, coupon_id, created_at, updated_at
                FROM carts
                WHERE id = :cartId""")
                .param("cartId", cartId)
                .query(CART_MAPPER)
                .optional();
    }

    UUID createCart(UUID storeId, UUID userId, String guestToken) {
        return jdbc.sql("""
                INSERT INTO carts (store_id, user_id, guest_token, status)
                VALUES (:storeId, :userId, :guestToken, 'ACTIVE')
                RETURNING id""")
                .param("storeId", storeId)
                .param("userId", userId)
                .param("guestToken", guestToken)
                .query(UUID.class)
                .single();
    }

    List<CartItemRow> findItems(UUID cartId) {
        return jdbc.sql("""
                SELECT id, cart_id, product_id, quantity, created_at, updated_at
                FROM cart_items
                WHERE cart_id = :cartId
                ORDER BY created_at ASC""")
                .param("cartId", cartId)
                .query(ITEM_MAPPER)
                .list();
    }

    Optional<CartItemRow> findItemById(UUID itemId) {
        return jdbc.sql("""
                SELECT id, cart_id, product_id, quantity, created_at, updated_at
                FROM cart_items
                WHERE id = :itemId""")
                .param("itemId", itemId)
                .query(ITEM_MAPPER)
                .optional();
    }

    Optional<CartItemRow> findItemByProduct(UUID cartId, UUID productId) {
        return jdbc.sql("""
                SELECT id, cart_id, product_id, quantity, created_at, updated_at
                FROM cart_items
                WHERE cart_id = :cartId AND product_id = :productId""")
                .param("cartId", cartId)
                .param("productId", productId)
                .query(ITEM_MAPPER)
                .optional();
    }

    void insertItem(UUID cartId, UUID productId, int quantity) {
        jdbc.sql("""
                INSERT INTO cart_items (cart_id, product_id, quantity)
                VALUES (:cartId, :productId, :quantity)""")
                .param("cartId", cartId)
                .param("productId", productId)
                .param("quantity", quantity)
                .update();
    }

    void updateItemQuantity(UUID itemId, int quantity) {
        jdbc.sql("UPDATE cart_items SET quantity = :quantity WHERE id = :id")
                .param("id", itemId)
                .param("quantity", quantity)
                .update();
    }

    void deleteItem(UUID itemId) {
        jdbc.sql("DELETE FROM cart_items WHERE id = :id")
                .param("id", itemId)
                .update();
    }

    void setCoupon(UUID cartId, UUID couponId) {
        jdbc.sql("UPDATE carts SET coupon_id = :couponId WHERE id = :cartId")
                .param("cartId", cartId)
                .param("couponId", couponId)
                .update();
    }

    void removeCoupon(UUID cartId) {
        jdbc.sql("UPDATE carts SET coupon_id = NULL WHERE id = :cartId")
                .param("cartId", cartId)
                .update();
    }

    Optional<CouponRow> findCouponByCode(String code, UUID storeId) {
        return jdbc.sql("""
                SELECT id, store_id, code, discount_type, discount_value, min_order_amount,
                       max_redemptions, valid_from, valid_until, active
                FROM coupons
                WHERE lower(code) = lower(:code) AND (store_id IS NULL OR store_id = :storeId)""")
                .param("code", code)
                .param("storeId", storeId)
                .query(COUPON_MAPPER)
                .optional();
    }

    Optional<CouponRow> findCouponById(UUID couponId) {
        return jdbc.sql("""
                SELECT id, store_id, code, discount_type, discount_value, min_order_amount,
                       max_redemptions, valid_from, valid_until, active
                FROM coupons
                WHERE id = :couponId""")
                .param("couponId", couponId)
                .query(COUPON_MAPPER)
                .optional();
    }

    int countCouponRedemptions(UUID couponId) {
        return jdbc.sql("SELECT COUNT(*) FROM coupon_redemptions WHERE coupon_id = :couponId")
                .param("couponId", couponId)
                .query(Integer.class)
                .single();
    }

    void markMerged(UUID guestCartId, UUID userCartId) {
        jdbc.sql("""
                UPDATE carts
                SET status = 'MERGED', merged_into_cart_id = :userCartId
                WHERE id = :guestCartId""")
                .param("guestCartId", guestCartId)
                .param("userCartId", userCartId)
                .update();
    }

    void markConverted(UUID cartId) {
        jdbc.sql("UPDATE carts SET status = 'CONVERTED' WHERE id = :cartId")
                .param("cartId", cartId)
                .update();
    }
}
