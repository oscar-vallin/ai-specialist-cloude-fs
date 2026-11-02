package com.bookstore.ecommerce.shipping;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookstore.ecommerce.shipping.ShippingRateService.Quote;
import com.bookstore.ecommerce.shipping.ShippingRateService.RateBand;
import com.bookstore.ecommerce.store.StoreService;
import com.bookstore.ecommerce.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ShippingRateIntegrationTest extends IntegrationTest {

    @Autowired
    private ShippingRateService shippingRateService;

    @Autowired
    private StoreService storeService;

    private UUID defaultStoreId;

    @BeforeEach
    void initStore() {
        defaultStoreId = storeService.resolveStoreId(null);
    }

    @Test
    void findsUSRateBandsCorrectly() {
        Optional<RateBand> bandLight = shippingRateService.findBand(defaultStoreId, "US", 250);
        assertThat(bandLight).isPresent();
        assertThat(bandLight.get().rate()).isEqualByComparingTo(new BigDecimal("3.99"));
        assertThat(bandLight.get().estimatedDaysMin()).isEqualTo(3);
        assertThat(bandLight.get().estimatedDaysMax()).isEqualTo(5);

        Optional<RateBand> bandMedium = shippingRateService.findBand(defaultStoreId, "US", 750);
        assertThat(bandMedium).isPresent();
        assertThat(bandMedium.get().rate()).isEqualByComparingTo(new BigDecimal("5.99"));
        assertThat(bandMedium.get().estimatedDaysMin()).isEqualTo(3);
        assertThat(bandMedium.get().estimatedDaysMax()).isEqualTo(6);
    }

    @Test
    void quoteAppliesFreeShippingForOrdersAboveThreshold() {
        Optional<Quote> quote = shippingRateService.quote(defaultStoreId, "US", 400, new BigDecimal("60.00"));

        assertThat(quote).isPresent();
        assertThat(quote.get().freeShippingApplied()).isTrue();
        assertThat(quote.get().rate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(quote.get().freeShippingThreshold()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void quoteAppliesStandardRateForOrdersBelowThreshold() {
        Optional<Quote> quote = shippingRateService.quote(defaultStoreId, "US", 400, new BigDecimal("20.00"));

        assertThat(quote).isPresent();
        assertThat(quote.get().freeShippingApplied()).isFalse();
        assertThat(quote.get().rate()).isEqualByComparingTo(new BigDecimal("3.99"));
    }

    @Test
    void quoteReturnsEmptyWhenCountryNotSupported() {
        Optional<Quote> quote = shippingRateService.quote(defaultStoreId, "ZZ", 400, new BigDecimal("100.00"));

        assertThat(quote).isEmpty();
    }
}
