package com.bookstore.ecommerce.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bookstore.ecommerce.shipping.ShippingRateService.Quote;
import com.bookstore.ecommerce.shipping.ShippingRateService.RateBand;
import com.bookstore.ecommerce.store.StoreService;
import com.bookstore.ecommerce.store.StoreService.StorePolicy;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

class ShippingRateServiceTest {

    private JdbcClient jdbc;
    private StoreService storeService;
    private ShippingRateServiceImpl service;

    private final UUID storeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcClient.class);
        storeService = mock(StoreService.class);
        service = new ShippingRateServiceImpl(jdbc, storeService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findBandReturnsBandWhenMatching() {
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<RateBand> querySpec = mock(JdbcClient.MappedQuerySpec.class);
        RateBand expectedBand = new RateBand(new BigDecimal("4.99"), 3, 5);

        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.param(eq("store"), eq(storeId))).thenReturn(spec);
        when(spec.param(eq("country"), eq("US"))).thenReturn(spec);
        when(spec.param(eq("w"), eq(400))).thenReturn(spec);
        when(spec.query(any(RowMapper.class))).thenReturn(querySpec);
        when(querySpec.optional()).thenReturn(Optional.of(expectedBand));

        Optional<RateBand> result = service.findBand(storeId, "US", 400);

        assertThat(result).contains(expectedBand);
    }

    @Test
    @SuppressWarnings("unchecked")
    void quoteAppliesFreeShippingWhenSubtotalMeetsThreshold() {
        StorePolicy policy = new StorePolicy(storeId, 48, 7, new BigDecimal("35.00"), new BigDecimal("0.0825"), 100, 100);
        when(storeService.policy(storeId)).thenReturn(policy);

        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<RateBand> querySpec = mock(JdbcClient.MappedQuerySpec.class);
        RateBand band = new RateBand(new BigDecimal("5.99"), 3, 5);

        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.param(eq("store"), eq(storeId))).thenReturn(spec);
        when(spec.param(eq("country"), eq("US"))).thenReturn(spec);
        when(spec.param(eq("w"), eq(500))).thenReturn(spec);
        when(spec.query(any(RowMapper.class))).thenReturn(querySpec);
        when(querySpec.optional()).thenReturn(Optional.of(band));

        Optional<Quote> quote = service.quote(storeId, "US", 500, new BigDecimal("45.00"));

        assertThat(quote).isPresent();
        assertThat(quote.get().freeShippingApplied()).isTrue();
        assertThat(quote.get().rate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(quote.get().band().rate()).isEqualByComparingTo(new BigDecimal("5.99"));
        assertThat(quote.get().freeShippingThreshold()).isEqualByComparingTo(new BigDecimal("35.00"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void quoteChargesStandardRateWhenSubtotalBelowThreshold() {
        StorePolicy policy = new StorePolicy(storeId, 48, 7, new BigDecimal("35.00"), new BigDecimal("0.0825"), 100, 100);
        when(storeService.policy(storeId)).thenReturn(policy);

        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<RateBand> querySpec = mock(JdbcClient.MappedQuerySpec.class);
        RateBand band = new RateBand(new BigDecimal("5.99"), 3, 5);

        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.param(eq("store"), eq(storeId))).thenReturn(spec);
        when(spec.param(eq("country"), eq("US"))).thenReturn(spec);
        when(spec.param(eq("w"), eq(500))).thenReturn(spec);
        when(spec.query(any(RowMapper.class))).thenReturn(querySpec);
        when(querySpec.optional()).thenReturn(Optional.of(band));

        Optional<Quote> quote = service.quote(storeId, "US", 500, new BigDecimal("20.00"));

        assertThat(quote).isPresent();
        assertThat(quote.get().freeShippingApplied()).isFalse();
        assertThat(quote.get().rate()).isEqualByComparingTo(new BigDecimal("5.99"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void quoteReturnsEmptyWhenBandNotFound() {
        StorePolicy policy = new StorePolicy(storeId, 48, 7, new BigDecimal("35.00"), new BigDecimal("0.0825"), 100, 100);
        when(storeService.policy(storeId)).thenReturn(policy);

        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<RateBand> querySpec = mock(JdbcClient.MappedQuerySpec.class);

        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.param(eq("store"), eq(storeId))).thenReturn(spec);
        when(spec.param(eq("country"), eq("ZZ"))).thenReturn(spec);
        when(spec.param(eq("w"), eq(500))).thenReturn(spec);
        when(spec.query(any(RowMapper.class))).thenReturn(querySpec);
        when(querySpec.optional()).thenReturn(Optional.empty());

        Optional<Quote> quote = service.quote(storeId, "ZZ", 500, new BigDecimal("50.00"));

        assertThat(quote).isEmpty();
    }
}
