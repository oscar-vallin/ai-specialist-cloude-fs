package com.bookstore.ecommerce.store;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.config.AppProperties;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class StoreServiceImpl implements StoreService {

    private final JdbcClient jdbc;
    private final AppProperties props;

    StoreServiceImpl(JdbcClient jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @Override
    @Transactional(readOnly = true)
    public UUID resolveStoreId(UUID storeId) {
        if (storeId != null) {
            return jdbc.sql("SELECT id FROM stores WHERE id = :id AND status = 'ACTIVE'").param("id", storeId)
                    .query(UUID.class).optional()
                    .orElseThrow(() -> ApiException.of(ErrorCode.STORE_NOT_FOUND, "The store does not exist."));
        }
        return jdbc.sql("SELECT id FROM stores WHERE name = :name AND status = 'ACTIVE'")
                .param("name", props.defaultStoreName()).query(UUID.class).optional()
                .orElseThrow(() -> ApiException.of(ErrorCode.STORE_NOT_FOUND, "The default store is not configured."));
    }

    @Override
    @Transactional(readOnly = true)
    public StorePolicy policy(UUID storeId) {
        return jdbc.sql("""
                SELECT store_id, cancellation_window_hours, return_window_days, free_shipping_threshold, tax_rate,
                       gift_points_per_currency_unit, max_gift_points_percent
                FROM store_policies WHERE store_id = :id""").param("id", storeId)
                .query((rs, n) -> new StorePolicy(rs.getObject("store_id", UUID.class),
                        rs.getInt("cancellation_window_hours"), rs.getInt("return_window_days"),
                        rs.getBigDecimal("free_shipping_threshold"), rs.getBigDecimal("tax_rate"),
                        rs.getInt("gift_points_per_currency_unit"), rs.getInt("max_gift_points_percent")))
                .optional()
                .orElseThrow(() -> ApiException.of(ErrorCode.STORE_NOT_FOUND, "The store has no policy."));
    }

    @Override
    @Transactional(readOnly = true)
    public StorePolicy defaultStorePolicy() {
        return policy(resolveStoreId(null));
    }
}
