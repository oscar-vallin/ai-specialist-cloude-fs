package com.bookstore.ecommerce.catalog;

import java.util.Map;
import java.util.UUID;

/**
 * Stock movements of the order lifecycle (docs/data-model.md section 6.2, rule 8). All methods join the caller's
 * transaction. E-books have no stock and are ignored.
 */
public interface InventoryService {

    /** Order placed: reserve. @throws com.bookstore.ecommerce.common.ApiException INSUFFICIENT_STOCK */
    void reserve(Map<UUID, Integer> quantities);

    /** Payment failed or order cancelled before the sale: give the reservation back. */
    void release(Map<UUID, Integer> quantities);

    /** Order confirmed: the reserved units are sold (available and reserved both go down). */
    void commitSale(Map<UUID, Integer> quantities);

    /** Order cancelled after confirmation, or returned: put sold units back on the shelf. */
    void restock(Map<UUID, Integer> quantities);
}
