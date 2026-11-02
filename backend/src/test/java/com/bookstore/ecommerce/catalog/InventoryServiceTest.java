package com.bookstore.ecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.ErrorCode;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class InventoryServiceTest {

    private JdbcClient jdbc;
    private InventoryServiceImpl service;

    private final UUID productId1 = UUID.randomUUID();
    private final UUID productId2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcClient.class);
        service = new InventoryServiceImpl(jdbc);
    }

    @Test
    void reserveSucceedsWhenStockAvailable() {
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);

        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.param(eq("q"), any())).thenReturn(spec);
        when(spec.param(eq("id"), any())).thenReturn(spec);
        when(spec.update()).thenReturn(1);

        service.reserve(Map.of(productId1, 2));

        verify(spec).param("q", 2);
        verify(spec).param("id", productId1);
        verify(spec).update();
    }

    @Test
    void reserveThrowsWhenStockInsufficientForPhysicalProduct() {
        JdbcClient.StatementSpec updateSpec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.StatementSpec querySpec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<Boolean> mappedQuerySpec = mock(JdbcClient.MappedQuerySpec.class);

        when(jdbc.sql(anyString())).thenReturn(updateSpec);
        when(updateSpec.param(eq("q"), any())).thenReturn(updateSpec);
        when(updateSpec.param(eq("id"), eq(productId1))).thenReturn(updateSpec);
        when(updateSpec.update()).thenReturn(0);

        when(updateSpec.query(Boolean.class)).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.optional()).thenReturn(Optional.of(true)); // isPhysical = true

        assertThatThrownBy(() -> service.reserve(Map.of(productId1, 5)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK));
    }

    @Test
    void releaseUpdatesReservedQuantity() {
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);

        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.param(eq("q"), any())).thenReturn(spec);
        when(spec.param(eq("id"), any())).thenReturn(spec);
        when(spec.update()).thenReturn(1);

        service.release(Map.of(productId1, 3));

        verify(spec).param("q", 3);
        verify(spec).param("id", productId1);
        verify(spec).update();
    }

    @Test
    void commitSaleUpdatesAvailableAndReserved() {
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);

        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.param(eq("q"), any())).thenReturn(spec);
        when(spec.param(eq("id"), any())).thenReturn(spec);
        when(spec.update()).thenReturn(1);

        service.commitSale(Map.of(productId1, 2, productId2, 1));

        verify(spec).param("q", 2);
        verify(spec).param("q", 1);
    }

    @Test
    void restockIncreasesAvailableQuantity() {
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);

        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.param(eq("q"), any())).thenReturn(spec);
        when(spec.param(eq("id"), any())).thenReturn(spec);
        when(spec.update()).thenReturn(1);

        service.restock(Map.of(productId1, 5));

        verify(spec).param("q", 5);
        verify(spec).param("id", productId1);
        verify(spec).update();
    }
}
