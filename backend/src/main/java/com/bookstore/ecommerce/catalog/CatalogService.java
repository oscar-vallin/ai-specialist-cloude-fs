package com.bookstore.ecommerce.catalog;

import com.bookstore.ecommerce.common.Caller;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** What other domains (cart, order, shipping) may ask the catalog. */
public interface CatalogService {

    /** The product when it is {@code ACTIVE} and visible to the caller in the store. */
    Optional<ProductInfo> findVisible(UUID productId, UUID storeId, Caller caller);

    /** Products by id regardless of status or visibility (order snapshots, buy again). Unknown ids are absent. */
    List<ProductInfo> findByIds(Collection<UUID> productIds);

    /** Whether the product is visible to the caller in the store (catalog entitlement). */
    boolean isVisible(UUID productId, UUID storeId, Caller caller);

    /**
     * @param available units that can still be bought: stock minus reservations for physical books,
     *                  {@code Integer.MAX_VALUE} for e-books
     */
    record ProductInfo(UUID id, String isbn, String title, String format, BigDecimal price, Integer weightGrams,
            String status, String coverFrontUrl, int available) {

        public boolean physical() {
            return !"EBOOK".equals(format);
        }

        public boolean active() {
            return "ACTIVE".equals(status);
        }
    }
}
