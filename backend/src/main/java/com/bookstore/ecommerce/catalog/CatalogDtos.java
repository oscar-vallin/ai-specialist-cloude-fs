package com.bookstore.ecommerce.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response bodies of the catalog (see api/openapi.yaml, tag Catalog). */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record Category(UUID id, UUID parentId, String name, String slug, int displayOrder) {
    }

    public record CategoryRef(UUID id, String name, String slug) {
    }

    public record Brand(UUID id, String name, String description) {
    }

    public record BrandRef(UUID id, String name) {
    }

    public record AuthorRef(UUID id, String name) {
    }

    public record ProductSummary(UUID id, String isbn, String title, String tagline, String format, String language,
            BigDecimal price, String currency, BrandRef brand, List<AuthorRef> authors, CategoryRef primaryCategory,
            String coverFrontUrl, String stockStatus) {
    }

    public record ProductDetail(UUID id, String isbn, String title, String tagline, String format, String language,
            BigDecimal price, String currency, BrandRef brand, List<AuthorRef> authors, CategoryRef primaryCategory,
            String coverFrontUrl, String stockStatus, String description, List<CategoryRef> categories,
            String coverBackUrl, Integer weightGrams, LocalDate publicationDate, Double averageRating,
            long reviewCount, LocalDate tentativeDeliveryDate, String deliveryCountry) {
    }

    public record RelatedProduct(String relationType, ProductSummary product) {
    }
}
