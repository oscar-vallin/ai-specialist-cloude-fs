package com.bookstore.ecommerce.shipping;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Shipping rate lookup, used by catalog (delivery date) and order (checkout shipping cost). */
public interface ShippingRateService {

    /** The weight band of the store for the country that contains {@code weightGrams}, if any. */
    Optional<RateBand> findBand(UUID storeId, String country, int weightGrams);

    /**
     * Rate to charge for a parcel. Free (0.00) when the store has a free-shipping threshold and
     * {@code orderSubtotal} reaches it. Empty when no band matches.
     */
    Optional<Quote> quote(UUID storeId, String country, int weightGrams, BigDecimal orderSubtotal);

    record RateBand(BigDecimal rate, int estimatedDaysMin, int estimatedDaysMax) {
    }

    record Quote(RateBand band, BigDecimal rate, boolean freeShippingApplied, BigDecimal freeShippingThreshold) {
    }
}
