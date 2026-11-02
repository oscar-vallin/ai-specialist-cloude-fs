package com.bookstore.ecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class InventoryIntegrationTest extends IntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private TransactionTemplate tx;

    private UUID findPhysicalBook() {
        return jdbc.queryForObject(
                "SELECT p.id FROM products p JOIN inventory i ON i.product_id = p.id WHERE p.format <> 'EBOOK' AND i.quantity_available >= 5 LIMIT 1",
                UUID.class);
    }

    private int getReserved(UUID productId) {
        return jdbc.queryForObject("SELECT quantity_reserved FROM inventory WHERE product_id = ?", Integer.class, productId);
    }

    private int getAvailable(UUID productId) {
        return jdbc.queryForObject("SELECT quantity_available FROM inventory WHERE product_id = ?", Integer.class, productId);
    }

    @Test
    void reserveAndReleaseStockCycle() {
        UUID productId = findPhysicalBook();
        int initialReserved = getReserved(productId);

        tx.executeWithoutResult(status -> inventoryService.reserve(Map.of(productId, 2)));
        assertThat(getReserved(productId)).isEqualTo(initialReserved + 2);

        tx.executeWithoutResult(status -> inventoryService.release(Map.of(productId, 2)));
        assertThat(getReserved(productId)).isEqualTo(initialReserved);
    }

    @Test
    void commitSaleReducesAvailableAndReserved() {
        UUID productId = findPhysicalBook();
        int initialAvailable = getAvailable(productId);
        int initialReserved = getReserved(productId);

        tx.executeWithoutResult(status -> {
            inventoryService.reserve(Map.of(productId, 1));
            inventoryService.commitSale(Map.of(productId, 1));
        });

        assertThat(getAvailable(productId)).isEqualTo(initialAvailable - 1);
        assertThat(getReserved(productId)).isEqualTo(initialReserved);
    }

    @Test
    void restockIncreasesAvailableQuantity() {
        UUID productId = findPhysicalBook();
        int initialAvailable = getAvailable(productId);

        tx.executeWithoutResult(status -> inventoryService.restock(Map.of(productId, 5)));

        assertThat(getAvailable(productId)).isEqualTo(initialAvailable + 5);
    }

    @Test
    void reserveThrowsWhenExceedingAvailableStock() {
        UUID productId = findPhysicalBook();
        int available = getAvailable(productId);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> inventoryService.reserve(Map.of(productId, available + 100))))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK));
    }
}
