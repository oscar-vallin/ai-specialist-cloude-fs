package com.bookstore.ecommerce.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookstore.ecommerce.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CatalogApiTest extends IntegrationTest {

    private String productId(String title) {
        return jdbc.queryForObject("SELECT id::text FROM products WHERE title = ?", String.class, title);
    }

    @Test
    void guestsBrowseWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.content[0].price").value(org.hamcrest.Matchers.matchesPattern("\\d+\\.\\d{2}")))
                .andExpect(jsonPath("$.content[0].currency").value("USD"));
        mvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/brands")).andExpect(status().isOk());
    }

    @Test
    void membersOnlyBooksAreHiddenFromGuestsAndVisibleToCustomers() throws Exception {
        String id = productId("Members' Edition: The Annotated Holmes");
        mvc.perform(get("/api/v1/products/" + id))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mvc.perform(get("/api/v1/products").param("q", "Members' Edition"))
                .andExpect(jsonPath("$.totalElements").value(0));
        String auth = bearer(customerToken());
        mvc.perform(get("/api/v1/products/" + id).header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products").param("q", "Members' Edition").header("Authorization", auth))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void searchFiltersAndSorting() throws Exception {
        mvc.perform(get("/api/v1/products").param("q", "holmes"))
                .andExpect(jsonPath("$.content[0].title").value("The Adventures of Sherlock Holmes"));
        mvc.perform(get("/api/v1/products").param("q", "jane austen"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/products").param("q", "no such book anywhere"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/products").param("q", "austen"))
                .andExpect(jsonPath("$.content[0].title").value("Pride and Prejudice"));
        mvc.perform(get("/api/v1/products").param("format", "EBOOK").param("size", "100"))
                .andExpect(jsonPath("$.content[?(@.format != 'EBOOK')]").isEmpty());
        mvc.perform(get("/api/v1/products").param("sort", "price,desc").param("size", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(3));
        mvc.perform(get("/api/v1/products").param("minPrice", "10.00").param("maxPrice", "12.00"))
                .andExpect(status().isOk());
    }

    @Test
    void categoryFilterIncludesSubCategories() throws Exception {
        String fiction = jdbc.queryForObject("SELECT id::text FROM categories WHERE slug = 'fiction'", String.class);
        String science = jdbc.queryForObject("SELECT id::text FROM categories WHERE slug = 'science-fiction'", String.class);
        long all = json(mvc.perform(get("/api/v1/products").param("categoryId", fiction)).andReturn()).get("totalElements").asLong();
        long sub = json(mvc.perform(get("/api/v1/products").param("categoryId", science)).andReturn()).get("totalElements").asLong();
        org.assertj.core.api.Assertions.assertThat(all).isGreaterThan(sub);
        org.assertj.core.api.Assertions.assertThat(sub).isGreaterThan(0);
    }

    @Test
    void invalidParametersReturn400() throws Exception {
        mvc.perform(get("/api/v1/products").param("size", "1000"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/products").param("q", "a"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/products").param("sort", "isbn,asc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products").param("minPrice", "20.00").param("maxPrice", "10.00"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownStoreAndProductReturn404() throws Exception {
        mvc.perform(get("/api/v1/products").param("storeId", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        mvc.perform(get("/api/v1/products/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void productDetailHasTentativeDeliveryDateFromShippingRate() throws Exception {
        String id = productId("Pride and Prejudice");
        // Paperback of about 350 to 650 g in the US: band 0-500 (5 days) or 500-1000 (6 days).
        var body = json(mvc.perform(get("/api/v1/products/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryCountry").value("US")).andReturn());
        LocalDate date = LocalDate.parse(body.get("tentativeDeliveryDate").asText());
        int days = (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(clock), date);
        org.assertj.core.api.Assertions.assertThat(days).isBetween(5, 6);
        // Canada adds two days.
        var ca = json(mvc.perform(get("/api/v1/products/" + id).param("country", "CA")).andReturn());
        org.assertj.core.api.Assertions.assertThat(LocalDate.parse(ca.get("tentativeDeliveryDate").asText()))
                .isEqualTo(date.plusDays(2));
        // A country without rates has no estimate.
        mvc.perform(get("/api/v1/products/" + id).param("country", "ZZ"))
                .andExpect(jsonPath("$.tentativeDeliveryDate").doesNotExist());
    }

    @Test
    void ebooksHaveNoDeliveryDateAndAreAlwaysInStock() throws Exception {
        String id = productId("The War of the Worlds");
        mvc.perform(get("/api/v1/products/" + id))
                .andExpect(jsonPath("$.tentativeDeliveryDate").doesNotExist())
                .andExpect(jsonPath("$.stockStatus").value("IN_STOCK"));
    }

    @Test
    void relatedProductsExcludeTheProductItself() throws Exception {
        String id = productId("Frankenstein");
        mvc.perform(get("/api/v1/products/" + id + "/related").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.product.id == '" + id + "')]").isEmpty())
                .andExpect(jsonPath("$[0].relationType").exists());
    }

    @Test
    void recommendedRequiresLoginAndFallsBackToBestsellers() throws Exception {
        mvc.perform(get("/api/v1/products/recommended")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/products/recommended").header("Authorization", bearer(customerToken())).param("limit", "4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
    }
}
