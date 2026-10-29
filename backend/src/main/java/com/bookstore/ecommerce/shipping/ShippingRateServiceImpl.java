package com.bookstore.ecommerce.shipping;

import com.bookstore.ecommerce.store.StoreService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ShippingRateServiceImpl implements ShippingRateService {

    private final JdbcClient jdbc;
    private final StoreService stores;

    ShippingRateServiceImpl(JdbcClient jdbc, StoreService stores) {
        this.jdbc = jdbc;
        this.stores = stores;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RateBand> findBand(UUID storeId, String country, int weightGrams) {
        // Bands are [min, max): the lower bound is inclusive and the upper bound exclusive.
        return jdbc.sql("""
                SELECT rate, estimated_days_min, estimated_days_max FROM shipping_rates
                WHERE store_id = :store AND country = :country AND min_weight_grams <= :w AND :w < max_weight_grams""")
                .param("store", storeId).param("country", country).param("w", weightGrams)
                .query((rs, n) -> new RateBand(rs.getBigDecimal("rate"), rs.getInt("estimated_days_min"),
                        rs.getInt("estimated_days_max")))
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Quote> quote(UUID storeId, String country, int weightGrams, BigDecimal orderSubtotal) {
        BigDecimal threshold = stores.policy(storeId).freeShippingThreshold();
        return findBand(storeId, country, weightGrams).map(band -> {
            boolean free = threshold != null && orderSubtotal != null && orderSubtotal.compareTo(threshold) >= 0;
            return new Quote(band, free ? BigDecimal.ZERO : band.rate(), free, threshold);
        });
    }
}
