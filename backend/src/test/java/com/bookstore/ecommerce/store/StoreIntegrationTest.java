package com.bookstore.ecommerce.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.store.StoreService.StorePolicy;
import com.bookstore.ecommerce.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StoreIntegrationTest extends IntegrationTest {

    @Autowired
    private StoreService storeService;

    @Test
    void resolvesDefaultStoreSuccessfully() {
        UUID storeId = storeService.resolveStoreId(null);
        assertThat(storeId).isNotNull();

        String storeName = jdbc.queryForObject("SELECT name FROM stores WHERE id = ?", String.class, storeId);
        assertThat(storeName).isEqualTo("Main Store");
    }

    @Test
    void resolvesExplicitActiveStoreId() {
        UUID storeId = storeService.resolveStoreId(null);
        UUID resolved = storeService.resolveStoreId(storeId);
        assertThat(resolved).isEqualTo(storeId);
    }

    @Test
    void resolveUnknownStoreThrowsNotFound() {
        UUID unknown = UUID.randomUUID();
        assertThatThrownBy(() -> storeService.resolveStoreId(unknown))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.STORE_NOT_FOUND));
    }

    @Test
    void defaultStorePolicyHasExpectedBusinessRules() {
        StorePolicy policy = storeService.defaultStorePolicy();

        assertThat(policy).isNotNull();
        assertThat(policy.cancellationWindowHours()).isEqualTo(48);
        assertThat(policy.returnWindowDays()).isEqualTo(7);
        assertThat(policy.giftPointsPerCurrencyUnit()).isEqualTo(100);
        assertThat(policy.maxGiftPointsPercent()).isEqualTo(100);
        assertThat(policy.freeShippingThreshold()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(policy.taxRate()).isEqualByComparingTo(new BigDecimal("0.0800"));
    }
}
