package com.bookstore.ecommerce.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.config.AppProperties;
import com.bookstore.ecommerce.store.StoreService.StorePolicy;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

class StoreServiceTest {

    private JdbcClient jdbc;
    private AppProperties props;
    private StoreServiceImpl service;

    private final UUID storeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcClient.class);
        props = new AppProperties(
                "USD",
                100,
                "Main Store",
                "US",
                new AppProperties.Order(30),
                new AppProperties.Jwt("secret123456789012345678901234567890", 60),
                new AppProperties.Payment("4000000000000002")
        );
        service = new StoreServiceImpl(jdbc, props);
    }

    @Test
    void resolveStoreIdReturnsGivenStoreWhenFound() {
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<UUID> querySpec = mock(JdbcClient.MappedQuerySpec.class);

        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.param("id", storeId)).thenReturn(spec);
        when(spec.query(UUID.class)).thenReturn(querySpec);
        when(querySpec.optional()).thenReturn(Optional.of(storeId));

        UUID resolved = service.resolveStoreId(storeId);

        assertThat(resolved).isEqualTo(storeId);
    }

    @Test
    void resolveStoreIdThrowsWhenExplicitStoreNotFound() {
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<UUID> querySpec = mock(JdbcClient.MappedQuerySpec.class);

        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.param("id", storeId)).thenReturn(spec);
        when(spec.query(UUID.class)).thenReturn(querySpec);
        when(querySpec.optional()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveStoreId(storeId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.STORE_NOT_FOUND));
    }

    @Test
    void resolveStoreIdResolvesDefaultStoreNameWhenNull() {
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<UUID> querySpec = mock(JdbcClient.MappedQuerySpec.class);

        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.param("name", "Main Store")).thenReturn(spec);
        when(spec.query(UUID.class)).thenReturn(querySpec);
        when(querySpec.optional()).thenReturn(Optional.of(storeId));

        UUID resolved = service.resolveStoreId(null);

        assertThat(resolved).isEqualTo(storeId);
    }

    @Test
    void resolveStoreIdThrowsWhenDefaultStoreNotFound() {
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<UUID> querySpec = mock(JdbcClient.MappedQuerySpec.class);

        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.param("name", "Main Store")).thenReturn(spec);
        when(spec.query(UUID.class)).thenReturn(querySpec);
        when(querySpec.optional()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveStoreId(null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.STORE_NOT_FOUND));
    }

    @Test
    @SuppressWarnings("unchecked")
    void policyReturnsStorePolicyWhenFound() {
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<StorePolicy> querySpec = mock(JdbcClient.MappedQuerySpec.class);
        StorePolicy expected = new StorePolicy(storeId, 48, 7, new BigDecimal("35.00"), new BigDecimal("0.0825"), 100, 100);

        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.param("id", storeId)).thenReturn(spec);
        when(spec.query(any(RowMapper.class))).thenReturn(querySpec);
        when(querySpec.optional()).thenReturn(Optional.of(expected));

        StorePolicy policy = service.policy(storeId);

        assertThat(policy).isEqualTo(expected);
        assertThat(policy.cancellationWindowHours()).isEqualTo(48);
        assertThat(policy.returnWindowDays()).isEqualTo(7);
        assertThat(policy.giftPointsPerCurrencyUnit()).isEqualTo(100);
        assertThat(policy.freeShippingThreshold()).isEqualByComparingTo(new BigDecimal("35.00"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void policyThrowsWhenNotFound() {
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<StorePolicy> querySpec = mock(JdbcClient.MappedQuerySpec.class);

        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.param("id", storeId)).thenReturn(spec);
        when(spec.query(any(RowMapper.class))).thenReturn(querySpec);
        when(querySpec.optional()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.policy(storeId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.STORE_NOT_FOUND));
    }
}
