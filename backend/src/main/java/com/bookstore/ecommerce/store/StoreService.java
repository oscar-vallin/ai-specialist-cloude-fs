package com.bookstore.ecommerce.store;

import java.math.BigDecimal;
import java.util.UUID;

/** What other domains may ask the store domain: which store to use and its policy. */
public interface StoreService {

    /** The given store, or the configured default store when {@code storeId} is null. @throws com.bookstore.ecommerce.common.ApiException STORE_NOT_FOUND */
    UUID resolveStoreId(UUID storeId);

    StorePolicy policy(UUID storeId);

    /** Policy of the configured default store. */
    StorePolicy defaultStorePolicy();

    record StorePolicy(UUID storeId, int cancellationWindowHours, int returnWindowDays,
            BigDecimal freeShippingThreshold, BigDecimal taxRate, int giftPointsPerCurrencyUnit,
            int maxGiftPointsPercent) {
    }
}
