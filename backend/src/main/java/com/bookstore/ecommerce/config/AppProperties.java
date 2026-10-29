package com.bookstore.ecommerce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code app.*} properties. Every value can be overridden through the environment. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String currency,
        int giftPointsPerCurrencyUnit,
        String defaultStoreName,
        String defaultCountry,
        Order order,
        Jwt jwt,
        Payment payment) {

    public record Order(int paymentWindowMinutes) {
    }

    public record Jwt(String secret, long expirationMinutes) {
    }

    public record Payment(String mockDeclineCardNumber) {
    }
}
