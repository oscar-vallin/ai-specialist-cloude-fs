package com.bookstore.ecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bookstore.ecommerce.catalog.CatalogDtos.Brand;
import com.bookstore.ecommerce.catalog.CatalogDtos.Category;
import com.bookstore.ecommerce.catalog.CatalogDtos.ProductDetail;
import com.bookstore.ecommerce.catalog.CatalogDtos.ProductSummary;
import com.bookstore.ecommerce.catalog.CatalogDtos.RelatedProduct;
import com.bookstore.ecommerce.catalog.CatalogRepository.ProductRow;
import com.bookstore.ecommerce.catalog.CatalogRepository.Rating;
import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.Caller;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.common.PageResponse;
import com.bookstore.ecommerce.config.AppProperties;
import com.bookstore.ecommerce.member.MemberAddressService;
import com.bookstore.ecommerce.shipping.ShippingRateService;
import com.bookstore.ecommerce.shipping.ShippingRateService.RateBand;
import com.bookstore.ecommerce.store.StoreService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogQueryServiceTest {

    private CatalogRepository repository;
    private StoreService stores;
    private ShippingRateService shippingRates;
    private MemberAddressService addresses;
    private AppProperties props;
    private Clock clock;
    private CatalogQueryService service;

    private final UUID storeId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID brandId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Caller guestCaller = Caller.GUEST;
    private final Caller customerCaller = new Caller(userId, Set.of("CUSTOMER"));

    @BeforeEach
    void setUp() {
        repository = mock(CatalogRepository.class);
        stores = mock(StoreService.class);
        shippingRates = mock(ShippingRateService.class);
        addresses = mock(MemberAddressService.class);
        props = new AppProperties(
                "USD",
                100,
                "Main Store",
                "US",
                new AppProperties.Order(30),
                new AppProperties.Jwt("secret123456789012345678901234567890", 60),
                new AppProperties.Payment("4000000000000002")
        );
        clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);

        when(stores.resolveStoreId(null)).thenReturn(storeId);
        when(stores.resolveStoreId(storeId)).thenReturn(storeId);

        service = new CatalogQueryService(repository, stores, shippingRates, addresses, props, clock);
    }

    private ProductRow sampleProductRow(UUID id, String format, Integer weightGrams) {
        return new ProductRow(id, "978-0-123456-47-2", "Test Book", "A Great Read", "Description", format, "EN",
                new BigDecimal("19.99"), brandId, "Test Brand", "http://cover.jpg", "http://back.jpg", weightGrams,
                LocalDate.of(2025, 1, 1), "ACTIVE", categoryId, "Fiction", "fiction", true, 10);
    }

    @Test
    void categoriesReturnsRootCategoriesWhenParentNull() {
        Category cat = new Category(categoryId, null, "Fiction", "fiction", 1);
        when(repository.categories(null)).thenReturn(List.of(cat));

        List<Category> result = service.categories(null);

        assertThat(result).containsExactly(cat);
    }

    @Test
    void categoriesThrowsWhenParentCategoryDoesNotExist() {
        UUID parentId = UUID.randomUUID();
        when(repository.categoryExists(parentId)).thenReturn(false);

        assertThatThrownBy(() -> service.categories(parentId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void brandsReturnsPagedBrands() {
        Brand brand = new Brand(brandId, "Penguin", "Publisher");
        when(repository.brands(eq(storeId), eq(guestCaller), eq(null), eq(0), eq(20)))
                .thenReturn(List.of(brand));
        when(repository.countBrands(eq(storeId), eq(guestCaller), eq(null))).thenReturn(1L);

        PageResponse<Brand> response = service.brands(storeId, guestCaller, null, 0, 20);

        assertThat(response.content()).containsExactly(brand);
        assertThat(response.totalElements()).isEqualTo(1L);
    }

    @Test
    void brandsThrowsWhenCategoryDoesNotExist() {
        when(repository.categoryExists(categoryId)).thenReturn(false);

        assertThatThrownBy(() -> service.brands(storeId, guestCaller, categoryId, 0, 20))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void searchValidatesMinPriceNotExceedingMaxPrice() {
        assertThatThrownBy(() -> service.search(storeId, guestCaller, null, null, null, null, null, null,
                new BigDecimal("50.00"), new BigDecimal("20.00"), null, 0, 20, "price,asc"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void searchReturnsProductSummaries() {
        ProductRow row = sampleProductRow(productId, "PAPERBACK", 400);
        when(repository.searchProducts(any(), eq("price"), eq(true), eq(0), eq(20)))
                .thenReturn(List.of(row));
        when(repository.countProducts(any())).thenReturn(1L);
        when(repository.authorsOf(List.of(productId))).thenReturn(Map.of());

        PageResponse<ProductSummary> response = service.search(storeId, guestCaller, "test", null, null, null,
                null, null, new BigDecimal("10.00"), new BigDecimal("30.00"), true, 0, 20, "price,asc");

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).title()).isEqualTo("Test Book");
        assertThat(response.totalElements()).isEqualTo(1L);
    }

    @Test
    void detailReturnsProductDetailWithCalculatedDeliveryDate() {
        ProductRow row = sampleProductRow(productId, "PAPERBACK", 450);
        when(repository.findVisible(productId, storeId, guestCaller)).thenReturn(Optional.of(row));
        when(repository.authorsOf(List.of(productId))).thenReturn(Map.of());
        when(repository.ratingOf(productId)).thenReturn(new Rating(4.55, 12L));
        when(repository.categoriesOf(productId)).thenReturn(List.of());
        when(shippingRates.findBand(storeId, "US", 450)).thenReturn(Optional.of(new RateBand(new BigDecimal("5.00"), 3, 5)));

        ProductDetail detail = service.detail(productId, storeId, guestCaller, null);

        assertThat(detail.id()).isEqualTo(productId);
        assertThat(detail.title()).isEqualTo("Test Book");
        assertThat(detail.averageRating()).isEqualTo(4.6);
        assertThat(detail.reviewCount()).isEqualTo(12L);
        assertThat(detail.deliveryCountry()).isEqualTo("US");
        assertThat(detail.tentativeDeliveryDate()).isEqualTo(LocalDate.of(2026, 6, 6)); // 2026-06-01 + 5 days
    }

    @Test
    void detailExcludesDeliveryDateForEbook() {
        ProductRow row = sampleProductRow(productId, "EBOOK", null);
        when(repository.findVisible(productId, storeId, guestCaller)).thenReturn(Optional.of(row));
        when(repository.authorsOf(List.of(productId))).thenReturn(Map.of());
        when(repository.ratingOf(productId)).thenReturn(new Rating(null, 0L));
        when(repository.categoriesOf(productId)).thenReturn(List.of());

        ProductDetail detail = service.detail(productId, storeId, guestCaller, null);

        assertThat(detail.tentativeDeliveryDate()).isNull();
        assertThat(detail.deliveryCountry()).isNull();
    }

    @Test
    void detailThrowsNotFoundWhenProductHiddenOrMissing() {
        when(repository.findVisible(productId, storeId, guestCaller)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(productId, storeId, guestCaller, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    void relatedReturnsCuratedAndSimilarProductsExcludingSelf() {
        UUID relatedId = UUID.randomUUID();
        ProductRow row = sampleProductRow(productId, "PAPERBACK", 300);
        ProductRow relatedRow = sampleProductRow(relatedId, "PAPERBACK", 300);

        when(repository.findVisible(productId, storeId, guestCaller)).thenReturn(Optional.of(row));
        when(repository.curatedRelations(productId))
                .thenReturn(List.of(new CatalogRepository.RelatedRow(relatedId, "UP_SELL")));
        when(repository.findVisible(relatedId, storeId, guestCaller)).thenReturn(Optional.of(relatedRow));
        when(repository.rowsById(List.of(relatedId))).thenReturn(Map.of(relatedId, relatedRow));
        when(repository.authorsOf(List.of(relatedId))).thenReturn(Map.of());

        List<RelatedProduct> result = service.related(productId, storeId, guestCaller, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).relationType()).isEqualTo("UP_SELL");
        assertThat(result.get(0).product().id()).isEqualTo(relatedId);
    }

    @Test
    void recommendedFallsBackToBestsellersWhenNoHistory() {
        UUID recId = UUID.randomUUID();
        ProductRow recRow = sampleProductRow(recId, "PAPERBACK", 300);

        when(repository.recommendedFromHistory(userId, storeId, customerCaller, 4)).thenReturn(List.of());
        when(repository.bestSellers(eq(storeId), eq(customerCaller), any(), eq(4))).thenReturn(List.of(recId));
        when(repository.rowsById(any())).thenReturn(Map.of(recId, recRow));
        when(repository.authorsOf(any())).thenReturn(Map.of());

        List<ProductSummary> result = service.recommended(storeId, customerCaller, 4);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(recId);
    }
}
