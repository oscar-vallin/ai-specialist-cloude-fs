package com.bookstore.ecommerce.catalog;

import com.bookstore.ecommerce.catalog.CatalogDtos.AuthorRef;
import com.bookstore.ecommerce.catalog.CatalogDtos.Brand;
import com.bookstore.ecommerce.catalog.CatalogDtos.BrandRef;
import com.bookstore.ecommerce.catalog.CatalogDtos.Category;
import com.bookstore.ecommerce.catalog.CatalogDtos.CategoryRef;
import com.bookstore.ecommerce.common.Caller;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** All SQL of the catalog. Visibility (entitlement) is applied in every product query by {@link #visibleSql}. */
@Repository
class CatalogRepository {

    /** Flat product row used to build summaries and details. */
    record ProductRow(UUID id, String isbn, String title, String tagline, String description, String format,
            String language, BigDecimal price, UUID brandId, String brandName, String coverFrontUrl,
            String coverBackUrl, Integer weightGrams, LocalDate publicationDate, String status,
            UUID primaryCategoryId, String primaryCategoryName, String primaryCategorySlug, boolean inStock,
            int available) {
    }

    private static final String PRODUCT_SELECT = """
            SELECT p.id, p.isbn, p.title, p.tagline, p.description, p.format, p.language, p.price, p.brand_id,
                   b.name AS brand_name, p.cover_front_url, p.cover_back_url, p.weight_grams, p.publication_date,
                   p.status, pc.category_id AS primary_category_id, c.name AS primary_category_name,
                   c.slug AS primary_category_slug,
                   (p.format = 'EBOOK' OR COALESCE(i.quantity_available - i.quantity_reserved, 0) > 0) AS in_stock,
                   CASE WHEN p.format = 'EBOOK' THEN 2147483647
                        ELSE GREATEST(COALESCE(i.quantity_available - i.quantity_reserved, 0), 0) END AS available
            FROM products p
            JOIN brands b ON b.id = p.brand_id
            LEFT JOIN inventory i ON i.product_id = p.id
            LEFT JOIN product_categories pc ON pc.product_id = p.id AND pc.is_primary
            LEFT JOIN categories c ON c.id = pc.category_id
            """;

    private static final RowMapper<ProductRow> PRODUCT = (rs, n) -> new ProductRow(rs.getObject("id", UUID.class),
            rs.getString("isbn"), rs.getString("title"), rs.getString("tagline"), rs.getString("description"),
            rs.getString("format"), rs.getString("language"), rs.getBigDecimal("price"),
            rs.getObject("brand_id", UUID.class), rs.getString("brand_name"), rs.getString("cover_front_url"),
            rs.getString("cover_back_url"), (Integer) rs.getObject("weight_grams"),
            rs.getObject("publication_date", LocalDate.class), rs.getString("status"),
            rs.getObject("primary_category_id", UUID.class), rs.getString("primary_category_name"),
            rs.getString("primary_category_slug"), rs.getBoolean("in_stock"), rs.getInt("available"));

    private final JdbcClient jdbc;

    CatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ----- visibility -----

    /**
     * SQL condition: product {@code p} is in at least one ACTIVE catalog of the store that the caller may see.
     * A catalog with no entitlement rows is visible to everyone; otherwise only to the listed roles;
     * administrators see every catalog. Binds {@code :store}, {@code :admin} and {@code :roles}.
     */
    static String visibleSql() {
        return """
                EXISTS (SELECT 1 FROM catalog_products cp JOIN catalogs ct ON ct.id = cp.catalog_id
                        WHERE cp.product_id = p.id AND ct.store_id = :store AND ct.status = 'ACTIVE'
                          AND (:admin
                               OR NOT EXISTS (SELECT 1 FROM catalog_entitlements ce WHERE ce.catalog_id = ct.id)
                               OR EXISTS (SELECT 1 FROM catalog_entitlements ce JOIN roles r ON r.id = ce.role_id
                                          WHERE ce.catalog_id = ct.id AND r.name IN (:roles))))""";
    }

    private static Map<String, Object> visibilityParams(UUID storeId, Caller caller) {
        Map<String, Object> params = new HashMap<>();
        params.put("store", storeId);
        params.put("admin", caller.isAdmin());
        params.put("roles", caller.isGuest() ? List.of("GUEST") : List.copyOf(caller.roles()));
        return params;
    }

    // ----- categories and brands -----

    List<Category> categories(UUID parentId) {
        String where = parentId == null ? "" : "WHERE parent_id = :parent";
        var spec = jdbc.sql("SELECT id, parent_id, name, slug, display_order FROM categories " + where
                + " ORDER BY display_order, name, id");
        if (parentId != null) {
            spec = spec.param("parent", parentId);
        }
        return spec.query((rs, n) -> new Category(rs.getObject("id", UUID.class), rs.getObject("parent_id", UUID.class),
                rs.getString("name"), rs.getString("slug"), rs.getInt("display_order"))).list();
    }

    boolean categoryExists(UUID id) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM categories WHERE id = :id)").param("id", id)
                .query(Boolean.class).single();
    }

    private static final String CATEGORY_TREE = """
            WITH RECURSIVE tree(id) AS (SELECT id FROM categories WHERE id = :category
                UNION ALL SELECT c.id FROM categories c JOIN tree t ON c.parent_id = t.id)
            SELECT id FROM tree""";

    long countBrands(UUID storeId, Caller caller, UUID categoryId) {
        Map<String, Object> params = visibilityParams(storeId, caller);
        String filter = brandFilter(categoryId, params);
        return jdbc.sql("SELECT count(*) FROM brands b WHERE " + filter).params(params).query(Long.class).single();
    }

    List<Brand> brands(UUID storeId, Caller caller, UUID categoryId, int page, int size) {
        Map<String, Object> params = visibilityParams(storeId, caller);
        String filter = brandFilter(categoryId, params);
        params.put("limit", size);
        params.put("offset", (long) page * size);
        return jdbc.sql("SELECT b.id, b.name, b.description FROM brands b WHERE " + filter
                + " ORDER BY b.name, b.id LIMIT :limit OFFSET :offset").params(params)
                .query((rs, n) -> new Brand(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("description"))).list();
    }

    private static String brandFilter(UUID categoryId, Map<String, Object> params) {
        if (categoryId == null) {
            return "TRUE";
        }
        params.put("category", categoryId);
        return "EXISTS (SELECT 1 FROM products p JOIN product_categories pcx ON pcx.product_id = p.id "
                + "WHERE p.brand_id = b.id AND p.status = 'ACTIVE' AND pcx.category_id IN (" + CATEGORY_TREE + ") AND "
                + visibleSql() + ")";
    }

    // ----- product search -----

    record Filter(UUID storeId, Caller caller, String q, UUID categoryId, UUID brandId, UUID authorId,
            String language, String format, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock) {
    }

    private static final Map<String, String> SORT_COLUMNS = Map.of("title", "p.title", "price", "p.price",
            "publicationDate", "p.publication_date");

    private static String where(Filter f, Map<String, Object> params) {
        params.putAll(visibilityParams(f.storeId(), f.caller()));
        List<String> parts = new ArrayList<>();
        parts.add("p.status = 'ACTIVE'");
        parts.add(visibleSql());
        if (f.q() != null) {
            params.put("q", "%" + f.q().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            parts.add("(p.title ILIKE :q OR p.tagline ILIKE :q OR EXISTS (SELECT 1 FROM product_authors pa "
                    + "JOIN authors a ON a.id = pa.author_id WHERE pa.product_id = p.id AND a.name ILIKE :q))");
        }
        if (f.categoryId() != null) {
            params.put("category", f.categoryId());
            parts.add("p.id IN (SELECT pcx.product_id FROM product_categories pcx WHERE pcx.category_id IN ("
                    + CATEGORY_TREE + "))");
        }
        if (f.brandId() != null) {
            params.put("brand", f.brandId());
            parts.add("p.brand_id = :brand");
        }
        if (f.authorId() != null) {
            params.put("author", f.authorId());
            parts.add("EXISTS (SELECT 1 FROM product_authors pax WHERE pax.product_id = p.id AND pax.author_id = :author)");
        }
        if (f.language() != null) {
            params.put("language", f.language());
            parts.add("p.language = :language");
        }
        if (f.format() != null) {
            params.put("format", f.format());
            parts.add("p.format = :format");
        }
        if (f.minPrice() != null) {
            params.put("minPrice", f.minPrice());
            parts.add("p.price >= :minPrice");
        }
        if (f.maxPrice() != null) {
            params.put("maxPrice", f.maxPrice());
            parts.add("p.price <= :maxPrice");
        }
        if (Boolean.TRUE.equals(f.inStock())) {
            parts.add("(p.format = 'EBOOK' OR COALESCE(i.quantity_available - i.quantity_reserved, 0) > 0)");
        }
        return String.join(" AND ", parts);
    }

    long countProducts(Filter f) {
        Map<String, Object> params = new HashMap<>();
        String where = where(f, params);
        return jdbc.sql("SELECT count(*) FROM products p LEFT JOIN inventory i ON i.product_id = p.id WHERE " + where)
                .params(params).query(Long.class).single();
    }

    List<ProductRow> searchProducts(Filter f, String sortField, boolean ascending, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        String where = where(f, params);
        String column = SORT_COLUMNS.get(sortField);
        String direction = ascending ? "ASC" : "DESC";
        params.put("limit", size);
        params.put("offset", (long) page * size);
        return jdbc.sql(PRODUCT_SELECT + " WHERE " + where + " ORDER BY " + column + " " + direction
                + " NULLS LAST, p.id LIMIT :limit OFFSET :offset").params(params).query(PRODUCT).list();
    }

    // ----- single products -----

    Optional<ProductRow> findVisible(UUID productId, UUID storeId, Caller caller) {
        Map<String, Object> params = visibilityParams(storeId, caller);
        params.put("id", productId);
        return jdbc.sql(PRODUCT_SELECT + " WHERE p.id = :id AND p.status = 'ACTIVE' AND " + visibleSql())
                .params(params).query(PRODUCT).optional();
    }

    List<ProductRow> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(PRODUCT_SELECT + " WHERE p.id IN (:ids)").param("ids", ids).query(PRODUCT).list();
    }

    boolean isVisible(UUID productId, UUID storeId, Caller caller) {
        Map<String, Object> params = visibilityParams(storeId, caller);
        params.put("id", productId);
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM products p WHERE p.id = :id AND p.status = 'ACTIVE' AND "
                + visibleSql() + ")").params(params).query(Boolean.class).single();
    }

    Map<UUID, List<AuthorRef>> authorsOf(Collection<UUID> productIds) {
        Map<UUID, List<AuthorRef>> result = new HashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        jdbc.sql("""
                SELECT pa.product_id, a.id, a.name FROM product_authors pa JOIN authors a ON a.id = pa.author_id
                WHERE pa.product_id IN (:ids) ORDER BY pa.product_id, pa.author_order, a.name""")
                .param("ids", productIds).query((rs, n) -> {
                    result.computeIfAbsent(rs.getObject("product_id", UUID.class), k -> new ArrayList<>())
                            .add(new AuthorRef(rs.getObject("id", UUID.class), rs.getString("name")));
                    return 0;
                }).list();
        return result;
    }

    List<CategoryRef> categoriesOf(UUID productId) {
        return jdbc.sql("""
                SELECT c.id, c.name, c.slug FROM product_categories pc JOIN categories c ON c.id = pc.category_id
                WHERE pc.product_id = :id ORDER BY pc.is_primary DESC, c.name""").param("id", productId)
                .query((rs, n) -> new CategoryRef(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("slug"))).list();
    }

    record Rating(Double average, long count) {
    }

    Rating ratingOf(UUID productId) {
        return jdbc.sql("SELECT avg(rating)::float8 AS avg, count(*) AS cnt FROM reviews WHERE product_id = :id")
                .param("id", productId)
                .query((rs, n) -> new Rating(rs.getObject("avg", Double.class), rs.getLong("cnt"))).single();
    }

    // ----- related and recommended -----

    record RelatedRow(UUID productId, String relationType) {
    }

    List<RelatedRow> curatedRelations(UUID productId) {
        return jdbc.sql("SELECT related_product_id, relation_type FROM related_products WHERE product_id = :id "
                + "ORDER BY position, relation_type, related_product_id").param("id", productId)
                .query((rs, n) -> new RelatedRow(rs.getObject("related_product_id", UUID.class),
                        rs.getString("relation_type"))).list();
    }

    /** Products sharing the primary category or an author with the given product, best match first. */
    List<UUID> similarProducts(UUID productId, UUID storeId, Caller caller, int limit) {
        Map<String, Object> params = visibilityParams(storeId, caller);
        params.put("id", productId);
        params.put("limit", limit);
        return jdbc.sql("""
                SELECT p.id FROM products p
                WHERE p.id <> :id AND p.status = 'ACTIVE' AND %s
                  AND (EXISTS (SELECT 1 FROM product_categories a JOIN product_categories b
                               ON a.category_id = b.category_id
                               WHERE a.product_id = :id AND a.is_primary AND b.product_id = p.id)
                       OR EXISTS (SELECT 1 FROM product_authors x JOIN product_authors y ON x.author_id = y.author_id
                                  WHERE x.product_id = :id AND y.product_id = p.id))
                ORDER BY (EXISTS (SELECT 1 FROM product_authors x JOIN product_authors y ON x.author_id = y.author_id
                                  WHERE x.product_id = :id AND y.product_id = p.id)) DESC, p.title, p.id
                LIMIT :limit""".formatted(visibleSql())).params(params).query(UUID.class).list();
    }

    /** Visible products the user has not bought, scored by categories and authors of the order history. */
    List<UUID> recommendedFromHistory(UUID userId, UUID storeId, Caller caller, int limit) {
        Map<String, Object> params = visibilityParams(storeId, caller);
        params.put("user", userId);
        params.put("limit", limit);
        return jdbc.sql("""
                WITH bought AS (
                    SELECT DISTINCT oi.product_id FROM order_items oi JOIN orders o ON o.id = oi.order_id
                    WHERE o.user_id = :user AND o.status <> 'PAYMENT_FAILED'),
                liked_categories AS (
                    SELECT pc.category_id, count(*) AS weight FROM bought b
                    JOIN product_categories pc ON pc.product_id = b.product_id GROUP BY pc.category_id),
                liked_authors AS (
                    SELECT pa.author_id, count(*) AS weight FROM bought b
                    JOIN product_authors pa ON pa.product_id = b.product_id GROUP BY pa.author_id)
                SELECT p.id FROM products p
                LEFT JOIN LATERAL (SELECT COALESCE(sum(lc.weight), 0) AS s FROM product_categories pc
                                   JOIN liked_categories lc ON lc.category_id = pc.category_id
                                   WHERE pc.product_id = p.id) cs ON TRUE
                LEFT JOIN LATERAL (SELECT COALESCE(sum(la.weight), 0) AS s FROM product_authors pa
                                   JOIN liked_authors la ON la.author_id = pa.author_id
                                   WHERE pa.product_id = p.id) au ON TRUE
                WHERE p.status = 'ACTIVE' AND %s AND p.id NOT IN (SELECT product_id FROM bought)
                  AND (cs.s + au.s * 2) > 0
                ORDER BY (cs.s + au.s * 2) DESC, p.title, p.id LIMIT :limit""".formatted(visibleSql()))
                .params(params).query(UUID.class).list();
    }

    boolean hasOrderHistory(UUID userId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM orders WHERE user_id = :user AND status <> 'PAYMENT_FAILED')")
                .param("user", userId).query(Boolean.class).single();
    }

    /** Best sellers by units in orders that were not abandoned; ties by title. */
    List<UUID> bestSellers(UUID storeId, Caller caller, Set<UUID> exclude, int limit) {
        Map<String, Object> params = visibilityParams(storeId, caller);
        params.put("limit", limit);
        params.put("exclude", exclude.isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(exclude));
        return jdbc.sql("""
                SELECT p.id FROM products p
                LEFT JOIN (SELECT oi.product_id, sum(oi.quantity) AS units FROM order_items oi
                           JOIN orders o ON o.id = oi.order_id WHERE o.status NOT IN ('PAYMENT_FAILED', 'CANCELLED')
                           GROUP BY oi.product_id) s ON s.product_id = p.id
                WHERE p.status = 'ACTIVE' AND %s AND p.id NOT IN (:exclude)
                ORDER BY COALESCE(s.units, 0) DESC, p.title, p.id LIMIT :limit""".formatted(visibleSql()))
                .params(params).query(UUID.class).list();
    }

    Map<UUID, ProductRow> rowsById(Collection<UUID> ids) {
        Map<UUID, ProductRow> map = new LinkedHashMap<>();
        findByIds(ids).forEach(r -> map.put(r.id(), r));
        return map;
    }

    BrandRef brandRef(ProductRow row) {
        return new BrandRef(row.brandId(), row.brandName());
    }
}
