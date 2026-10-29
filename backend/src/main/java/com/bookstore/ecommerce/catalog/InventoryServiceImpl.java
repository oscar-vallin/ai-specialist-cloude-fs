package com.bookstore.ecommerce.catalog;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.ErrorCode;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class InventoryServiceImpl implements InventoryService {

    private final JdbcClient jdbc;

    InventoryServiceImpl(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Map<UUID, Integer> quantities) {
        for (var e : sorted(quantities).entrySet()) {
            int updated = jdbc.sql("""
                    UPDATE inventory i SET quantity_reserved = quantity_reserved + :q, version = version + 1
                    FROM products p WHERE i.product_id = :id AND p.id = i.product_id AND p.format <> 'EBOOK'
                      AND i.quantity_available - i.quantity_reserved >= :q""")
                    .param("q", e.getValue()).param("id", e.getKey()).update();
            if (updated == 0 && isPhysical(e.getKey())) {
                throw ApiException.of(ErrorCode.INSUFFICIENT_STOCK, "Not enough stock for one of the books.");
            }
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Map<UUID, Integer> quantities) {
        for (var e : sorted(quantities).entrySet()) {
            jdbc.sql("""
                    UPDATE inventory SET quantity_reserved = GREATEST(quantity_reserved - :q, 0), version = version + 1
                    WHERE product_id = :id""").param("q", e.getValue()).param("id", e.getKey()).update();
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void commitSale(Map<UUID, Integer> quantities) {
        for (var e : sorted(quantities).entrySet()) {
            jdbc.sql("""
                    UPDATE inventory i SET quantity_available = quantity_available - :q,
                        quantity_reserved = quantity_reserved - :q, version = version + 1
                    FROM products p WHERE i.product_id = :id AND p.id = i.product_id AND p.format <> 'EBOOK'""")
                    .param("q", e.getValue()).param("id", e.getKey()).update();
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void restock(Map<UUID, Integer> quantities) {
        for (var e : sorted(quantities).entrySet()) {
            jdbc.sql("""
                    UPDATE inventory i SET quantity_available = quantity_available + :q, version = version + 1
                    FROM products p WHERE i.product_id = :id AND p.id = i.product_id AND p.format <> 'EBOOK'""")
                    .param("q", e.getValue()).param("id", e.getKey()).update();
        }
    }

    /** Fixed lock order (by id) so two orders with the same books cannot deadlock. */
    private static Map<UUID, Integer> sorted(Map<UUID, Integer> quantities) {
        return new TreeMap<>(quantities);
    }

    private boolean isPhysical(UUID productId) {
        return jdbc.sql("SELECT format <> 'EBOOK' FROM products WHERE id = :id").param("id", productId)
                .query(Boolean.class).optional().orElse(true);
    }
}
