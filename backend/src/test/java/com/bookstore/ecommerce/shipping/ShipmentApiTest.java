package com.bookstore.ecommerce.shipping;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookstore.ecommerce.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShipmentApiTest extends IntegrationTest {

    @Test
    void shippingRatesQuoteCalculatesBandsAndFreeShipping() throws Exception {
        // Under threshold: 500g physical book in US -> rate is charged
        mvc.perform(get("/api/v1/shipping/rates")
                .param("country", "US")
                .param("weightGrams", "500")
                .param("orderSubtotal", "20.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.country").value("US"))
                .andExpect(jsonPath("$.weightGrams").value(500))
                .andExpect(jsonPath("$.freeShippingApplied").value(false))
                .andExpect(jsonPath("$.rate").value("5.99"));

        // Above threshold (policy default is 50.00): 500g physical book in US -> rate is 0.00
        mvc.perform(get("/api/v1/shipping/rates")
                .param("country", "US")
                .param("weightGrams", "500")
                .param("orderSubtotal", "60.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freeShippingApplied").value(true))
                .andExpect(jsonPath("$.rate").value("0.00"));

        // Unavailable destination
        mvc.perform(get("/api/v1/shipping/rates")
                .param("country", "ZZ")
                .param("weightGrams", "500"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SHIPPING_UNAVAILABLE"));
    }

    @Test
    void deliveryEstimateForEbookIsNotShippable() throws Exception {
        // Find an ebook in seeded books
        UUID ebookId = jdbc.queryForObject("SELECT id FROM products WHERE format = 'EBOOK' LIMIT 1", UUID.class);

        mvc.perform(get("/api/v1/shipping/estimate")
                .param("productId", ebookId.toString())
                .param("country", "US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippable").value(false))
                .andExpect(jsonPath("$.tentativeDeliveryDate").doesNotExist());
    }
}
