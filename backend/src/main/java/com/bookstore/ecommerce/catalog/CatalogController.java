package com.bookstore.ecommerce.catalog;

import com.bookstore.ecommerce.catalog.CatalogDtos.Brand;
import com.bookstore.ecommerce.catalog.CatalogDtos.Category;
import com.bookstore.ecommerce.catalog.CatalogDtos.ProductDetail;
import com.bookstore.ecommerce.catalog.CatalogDtos.ProductSummary;
import com.bookstore.ecommerce.catalog.CatalogDtos.RelatedProduct;
import com.bookstore.ecommerce.common.Caller;
import com.bookstore.ecommerce.common.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
class CatalogController {

    private static final String MONEY = "^\\d{1,10}\\.\\d{2}$";

    private final CatalogQueryService service;

    CatalogController(CatalogQueryService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    List<Category> categories(@RequestParam(required = false) UUID parentId) {
        return service.categories(parentId);
    }

    @GetMapping("/brands")
    PageResponse<Brand> brands(Caller caller, @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID categoryId, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.brands(storeId, caller, categoryId, page, size);
    }

    @GetMapping("/products")
    PageResponse<ProductSummary> products(Caller caller, @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) @Size(min = 2, max = 100) String q,
            @RequestParam(required = false) UUID categoryId, @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) UUID authorId,
            @RequestParam(required = false) @Pattern(regexp = "^[a-z]{2}$") String language,
            @RequestParam(required = false) @Pattern(regexp = "^(PAPERBACK|HARDCOVER|EBOOK)$") String format,
            @RequestParam(required = false) @Pattern(regexp = MONEY) String minPrice,
            @RequestParam(required = false) @Pattern(regexp = MONEY) String maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "title,asc") @Pattern(regexp = "^(title|price|publicationDate)(,(asc|desc))?$") String sort) {
        return service.search(storeId, caller, q, categoryId, brandId, authorId, language, format,
                minPrice == null ? null : new BigDecimal(minPrice), maxPrice == null ? null : new BigDecimal(maxPrice),
                inStock, page, size, sort);
    }

    // Declared before /products/{productId}; the literal path also wins in Spring's matching.
    @GetMapping("/products/recommended")
    List<ProductSummary> recommended(Caller caller, @RequestParam(required = false) UUID storeId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int limit) {
        return service.recommended(storeId, caller, limit);
    }

    @GetMapping("/products/{productId}")
    ProductDetail product(Caller caller, @PathVariable UUID productId, @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) @Pattern(regexp = "^[A-Z]{2}$") String country) {
        return service.detail(productId, storeId, caller, country);
    }

    @GetMapping("/products/{productId}/related")
    List<RelatedProduct> related(Caller caller, @PathVariable UUID productId,
            @RequestParam(required = false) UUID storeId, @RequestParam(defaultValue = "10") @Min(1) @Max(20) int limit) {
        return service.related(productId, storeId, caller, limit);
    }
}
