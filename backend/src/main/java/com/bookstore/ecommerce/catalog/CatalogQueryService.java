package com.bookstore.ecommerce.catalog;

import com.bookstore.ecommerce.catalog.CatalogDtos.Brand;
import com.bookstore.ecommerce.catalog.CatalogDtos.Category;
import com.bookstore.ecommerce.catalog.CatalogDtos.ProductDetail;
import com.bookstore.ecommerce.catalog.CatalogDtos.ProductSummary;
import com.bookstore.ecommerce.catalog.CatalogDtos.RelatedProduct;
import com.bookstore.ecommerce.catalog.CatalogRepository.Filter;
import com.bookstore.ecommerce.catalog.CatalogRepository.ProductRow;
import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.Caller;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.common.PageResponse;
import com.bookstore.ecommerce.config.AppProperties;
import com.bookstore.ecommerce.member.MemberAddressService;
import com.bookstore.ecommerce.shipping.ShippingRateService;
import com.bookstore.ecommerce.store.StoreService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the catalog, and the {@link CatalogService} contract for other domains. */
@Service
@Transactional(readOnly = true)
class CatalogQueryService implements CatalogService {

    private final CatalogRepository repository;
    private final StoreService stores;
    private final ShippingRateService shippingRates;
    private final MemberAddressService addresses;
    private final AppProperties props;
    private final Clock clock;

    CatalogQueryService(CatalogRepository repository, StoreService stores, ShippingRateService shippingRates,
            MemberAddressService addresses, AppProperties props, Clock clock) {
        this.repository = repository;
        this.stores = stores;
        this.shippingRates = shippingRates;
        this.addresses = addresses;
        this.props = props;
        this.clock = clock;
    }

    List<Category> categories(UUID parentId) {
        if (parentId != null && !repository.categoryExists(parentId)) {
            throw ApiException.validation("parentId", "category does not exist");
        }
        return repository.categories(parentId);
    }

    PageResponse<Brand> brands(UUID storeId, Caller caller, UUID categoryId, int page, int size) {
        UUID store = stores.resolveStoreId(storeId);
        if (categoryId != null && !repository.categoryExists(categoryId)) {
            throw ApiException.validation("categoryId", "category does not exist");
        }
        return PageResponse.of(repository.brands(store, caller, categoryId, page, size), page, size,
                repository.countBrands(store, caller, categoryId));
    }

    PageResponse<ProductSummary> search(UUID storeId, Caller caller, String q, UUID categoryId, UUID brandId,
            UUID authorId, String language, String format, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock,
            int page, int size, String sort) {
        UUID store = stores.resolveStoreId(storeId);
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw ApiException.validation("minPrice", "must not exceed maxPrice");
        }
        String[] sortParts = sort.split(",");
        boolean ascending = sortParts.length < 2 || "asc".equals(sortParts[1]);
        Filter filter = new Filter(store, caller, q, categoryId, brandId, authorId, language, format, minPrice,
                maxPrice, inStock);
        List<ProductRow> rows = repository.searchProducts(filter, sortParts[0], ascending, page, size);
        return PageResponse.of(summaries(rows), page, size, repository.countProducts(filter));
    }

    ProductDetail detail(UUID productId, UUID storeId, Caller caller, String country) {
        UUID store = stores.resolveStoreId(storeId);
        ProductRow row = repository.findVisible(productId, store, caller)
                .orElseThrow(() -> ApiException.of(ErrorCode.PRODUCT_NOT_FOUND, "The product does not exist."));
        ProductSummary s = summaries(List.of(row)).get(0);
        var rating = repository.ratingOf(productId);
        String destination = country != null ? country
                : caller.isGuest() ? props.defaultCountry()
                : addresses.defaultAddressCountry(caller.userId()).orElse(props.defaultCountry());
        LocalDate delivery = null;
        if (!"EBOOK".equals(row.format()) && row.weightGrams() != null) {
            // FR-C4 / FR-SH2: today plus the estimated days of the shipping band for the product weight.
            delivery = shippingRates.findBand(store, destination, row.weightGrams())
                    .map(b -> LocalDate.now(clock).plusDays(b.estimatedDaysMax())).orElse(null);
        }
        Double average = rating.average() == null ? null : Math.round(rating.average() * 10) / 10.0;
        return new ProductDetail(s.id(), s.isbn(), s.title(), s.tagline(), s.format(), s.language(), s.price(),
                s.currency(), s.brand(), s.authors(), s.primaryCategory(), s.coverFrontUrl(), s.stockStatus(),
                row.description(), repository.categoriesOf(productId), row.coverBackUrl(), row.weightGrams(),
                row.publicationDate(), average, rating.count(), delivery, delivery == null ? null : destination);
    }

    List<RelatedProduct> related(UUID productId, UUID storeId, Caller caller, int limit) {
        UUID store = stores.resolveStoreId(storeId);
        repository.findVisible(productId, store, caller)
                .orElseThrow(() -> ApiException.of(ErrorCode.PRODUCT_NOT_FOUND, "The product does not exist."));
        List<CatalogRepository.RelatedRow> curated = repository.curatedRelations(productId);
        Map<UUID, String> types = new java.util.LinkedHashMap<>();
        curated.forEach(r -> types.putIfAbsent(r.productId(), r.relationType()));
        // Keep only visible, active products; then complete with similar ones.
        List<UUID> ids = new ArrayList<>();
        for (UUID id : types.keySet()) {
            if (ids.size() < limit && repository.findVisible(id, store, caller).isPresent()) {
                ids.add(id);
            }
        }
        if (ids.size() < limit) {
            for (UUID id : repository.similarProducts(productId, store, caller, limit)) {
                if (ids.size() < limit && !ids.contains(id)) {
                    ids.add(id);
                    types.putIfAbsent(id, "RELATED");
                }
            }
        }
        Map<UUID, ProductRow> rows = repository.rowsById(ids);
        List<ProductRow> ordered = ids.stream().map(rows::get).toList();
        List<ProductSummary> summaries = summaries(ordered);
        List<RelatedProduct> result = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            result.add(new RelatedProduct(types.get(ids.get(i)), summaries.get(i)));
        }
        return result;
    }

    List<ProductSummary> recommended(UUID storeId, Caller caller, int limit) {
        UUID store = stores.resolveStoreId(storeId);
        UUID userId = caller.requireUser();
        Set<UUID> ids = new LinkedHashSet<>(repository.recommendedFromHistory(userId, store, caller, limit));
        if (ids.size() < limit) {
            // No (or too little) history: complete with the current bestsellers (FR-C6).
            Set<UUID> exclude = new LinkedHashSet<>(ids);
            repository.bestSellers(store, caller, exclude, limit - ids.size()).forEach(ids::add);
        }
        Map<UUID, ProductRow> rows = repository.rowsById(ids);
        return summaries(ids.stream().map(rows::get).toList());
    }

    private List<ProductSummary> summaries(List<ProductRow> rows) {
        Map<UUID, List<CatalogDtos.AuthorRef>> authors = repository.authorsOf(rows.stream().map(ProductRow::id).toList());
        return rows.stream().map(r -> new ProductSummary(r.id(), r.isbn(), r.title(), r.tagline(), r.format(),
                r.language(), r.price(), props.currency(), repository.brandRef(r),
                authors.getOrDefault(r.id(), List.of()),
                r.primaryCategoryId() == null ? null
                        : new CatalogDtos.CategoryRef(r.primaryCategoryId(), r.primaryCategoryName(),
                                r.primaryCategorySlug()),
                r.coverFrontUrl(), r.inStock() ? "IN_STOCK" : "OUT_OF_STOCK")).toList();
    }

    // ----- CatalogService (for other domains) -----

    @Override
    public Optional<ProductInfo> findVisible(UUID productId, UUID storeId, Caller caller) {
        return repository.findVisible(productId, storeId, caller).map(CatalogQueryService::info);
    }

    @Override
    public List<ProductInfo> findByIds(Collection<UUID> productIds) {
        return repository.findByIds(productIds).stream().map(CatalogQueryService::info).toList();
    }

    @Override
    public boolean isVisible(UUID productId, UUID storeId, Caller caller) {
        return repository.isVisible(productId, storeId, caller);
    }

    private static ProductInfo info(ProductRow r) {
        return new ProductInfo(r.id(), r.isbn(), r.title(), r.format(), r.price(), r.weightGrams(), r.status(),
                r.coverFrontUrl(), r.available());
    }
}
