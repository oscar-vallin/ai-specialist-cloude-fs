package com.bookstore.ecommerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookstore.ecommerce.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class OrderAndPaymentFlowIntegrationTest extends IntegrationTest {

    @Test
    void completeFlow_register_browse_addToCart_checkoutWithGiftPoints_confirm_cancel_refund() throws Exception {
        String email = "flow-" + UUID.randomUUID() + "@bookstore.local";
        String password = "Password123#Flow";

        // 1. Register
        mvc.perform(withJson(post("/api/v1/auth/register"), Map.of(
                "email", email,
                "password", password,
                "firstName", "Flow",
                "lastName", "Tester")))
                .andExpect(status().isCreated());

        // 2. Login
        String token = login(email, password);
        assertThat(token).isNotEmpty();

        // Add saved address
        MvcResult addrResult = mvc.perform(withJson(post("/api/v1/users/me/addresses").header("Authorization", bearer(token)), Map.of(
                "firstName", "Flow",
                "lastName", "Tester",
                "line1", "123 Test St",
                "city", "Seattle",
                "state", "WA",
                "postalCode", "98101",
                "country", "US",
                "isDefault", true)))
                .andExpect(status().isCreated())
                .andReturn();
        String addressId = json(addrResult).get("id").asText();

        // Give user some gift points in database: 2000 points = $20.00
        UUID userId = UUID.fromString(json(mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(token))).andReturn()).get("id").asText());
        jdbc.update("UPDATE gift_point_accounts SET balance = 2000 WHERE user_id = ?", userId);

        // 3. Browse products
        MvcResult productsResult = mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode products = json(productsResult).get("content");
        assertThat(products.size()).isGreaterThan(0);

        String productId = products.get(0).get("id").asText();

        // 4. Add to cart
        mvc.perform(withJson(post("/api/v1/cart/items").header("Authorization", bearer(token)), Map.of(
                "productId", productId,
                "quantity", 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemCount").value(1));

        // 5. Checkout quote
        MvcResult quoteResult = mvc.perform(withJson(post("/api/v1/orders/checkout").header("Authorization", bearer(token)), Map.of(
                "addressId", addressId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.giftPointsBalance").value(2000))
                .andReturn();

        BigDecimal orderTotal = new BigDecimal(json(quoteResult).get("total").asText());

        // 6. Place order
        String orderIdempKey = "order-idemp-" + UUID.randomUUID();
        MvcResult placeResult = mvc.perform(withJson(post("/api/v1/orders")
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", orderIdempKey), Map.of(
                "addressId", addressId,
                "expectedTotal", orderTotal.toPlainString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andReturn();

        String orderId = json(placeResult).get("id").asText();

        // Test order idempotency replay
        mvc.perform(withJson(post("/api/v1/orders")
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", orderIdempKey), Map.of(
                "addressId", addressId,
                "expectedTotal", orderTotal.toPlainString())))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(orderId));

        // 7. Pay with gift points (500 points = $5.00) + card (the remaining)
        long pointsToUse = 500L;
        BigDecimal pointsValue = BigDecimal.valueOf(5.00);
        BigDecimal remainingCardAmount = orderTotal.subtract(pointsValue);

        String payIdempKey = "pay-idemp-" + UUID.randomUUID();
        Map<String, Object> payBody = Map.of(
                "orderId", orderId,
                "legs", List.of(
                        Map.of("method", "GIFT_POINTS", "points", pointsToUse),
                        Map.of("method", "CREDIT_CARD",
                                "amount", remainingCardAmount.toPlainString(),
                                "card", Map.of(
                                        "number", "4242424242424242",
                                        "expiryMonth", 12,
                                        "expiryYear", 2030,
                                        "cvv", "123"))));

        mvc.perform(withJson(post("/api/v1/payments")
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", payIdempKey), payBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.payments").isArray());

        // Check gift points balance decremented (2000 - 500 = 1500)
        mvc.perform(get("/api/v1/users/me/gift-points").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1500));

        // 8. Cancel order at t + 1 hour (within cancellation window)
        clock.advance(Duration.ofHours(1));
        token = login(email, password);

        mvc.perform(withJson(post("/api/v1/orders/" + orderId + "/cancel")
                .header("Authorization", bearer(token)), Map.of("reason", "Changed my mind")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refundedTotal").value(orderTotal.toPlainString()));

        // Check gift points refunded back (1500 + 500 = 2000)
        mvc.perform(get("/api/v1/users/me/gift-points").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(2000));
    }

    @Test
    void cancellationAfter48HoursIsRejected() throws Exception {
        String token = customerToken();

        // Find product & address
        UUID addressId = jdbc.queryForObject("SELECT id FROM addresses WHERE user_id = (SELECT id FROM users WHERE email = ?) LIMIT 1",
                UUID.class, CUSTOMER_EMAIL);
        UUID productId = jdbc.queryForObject("SELECT id FROM products WHERE status = 'ACTIVE' LIMIT 1", UUID.class);

        // Add to cart
        mvc.perform(withJson(post("/api/v1/cart/items").header("Authorization", bearer(token)), Map.of(
                "productId", productId.toString(),
                "quantity", 1)))
                .andExpect(status().isCreated());

        // Quote
        MvcResult quoteResult = mvc.perform(withJson(post("/api/v1/orders/checkout").header("Authorization", bearer(token)), Map.of(
                "addressId", addressId.toString())))
                .andExpect(status().isOk())
                .andReturn();
        String total = json(quoteResult).get("total").asText();

        // Place order
        MvcResult placeResult = mvc.perform(withJson(post("/api/v1/orders")
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", "idemp-" + UUID.randomUUID()), Map.of(
                "addressId", addressId.toString(),
                "expectedTotal", total)))
                .andExpect(status().isCreated())
                .andReturn();
        String orderId = json(placeResult).get("id").asText();

        // Pay order with card
        mvc.perform(withJson(post("/api/v1/payments")
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", "pay-" + UUID.randomUUID()), Map.of(
                "orderId", orderId,
                "legs", List.of(Map.of(
                        "method", "CREDIT_CARD",
                        "card", Map.of(
                                "number", "4242424242424242",
                                "expiryMonth", 12,
                                "expiryYear", 2030,
                                "cvv", "123"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.status").value("CONFIRMED"));

        // Advance clock by 49 hours (beyond 48 hour store window)
        clock.advance(Duration.ofHours(49));
        token = customerToken();

        // Cancellation should be rejected with 422 CANCELLATION_WINDOW_EXPIRED
        mvc.perform(withJson(post("/api/v1/orders/" + orderId + "/cancel")
                .header("Authorization", bearer(token)), Map.of("reason", "Too late")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CANCELLATION_WINDOW_EXPIRED"));
    }

    @Test
    void returnFlow_orderDelivered_requestReturn_approveReturn_deliverReturn_orderReturned() throws Exception {
        String token = customerToken();
        String admin = adminToken();

        UUID addressId = jdbc.queryForObject("SELECT id FROM addresses WHERE user_id = (SELECT id FROM users WHERE email = ?) LIMIT 1",
                UUID.class, CUSTOMER_EMAIL);
        UUID productId = jdbc.queryForObject("SELECT id FROM products WHERE status = 'ACTIVE' AND format = 'PAPERBACK' LIMIT 1", UUID.class);

        // Add to cart
        mvc.perform(withJson(post("/api/v1/cart/items").header("Authorization", bearer(token)), Map.of(
                "productId", productId.toString(),
                "quantity", 1)))
                .andExpect(status().isCreated());

        // Quote & Place
        MvcResult quote = mvc.perform(withJson(post("/api/v1/orders/checkout").header("Authorization", bearer(token)), Map.of(
                "addressId", addressId.toString()))).andReturn();
        String total = json(quote).get("total").asText();

        MvcResult place = mvc.perform(withJson(post("/api/v1/orders")
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", "idemp-ret-" + UUID.randomUUID()), Map.of(
                "addressId", addressId.toString(),
                "expectedTotal", total))).andReturn();
        String orderId = json(place).get("id").asText();

        // Pay
        MvcResult pay = mvc.perform(withJson(post("/api/v1/payments")
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", "pay-ret-" + UUID.randomUUID()), Map.of(
                "orderId", orderId,
                "legs", List.of(Map.of(
                        "method", "CREDIT_CARD",
                        "card", Map.of(
                                "number", "4242424242424242",
                                "expiryMonth", 12,
                                "expiryYear", 2030,
                                "cvv", "123"))))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode orderNode = json(pay).get("order");
        String outboundShipmentId = orderNode.get("shipments").get(0).get("id").asText();

        // Advance shipment: CREATED -> IN_TRANSIT (Order moves to SHIPPED)
        mvc.perform(withJson(patch("/api/v1/shipments/" + outboundShipmentId)
                .header("Authorization", bearer(admin)), Map.of(
                "status", "IN_TRANSIT",
                "carrier", "FedEx",
                "trackingNumber", "FX123456789")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));

        // Advance shipment: IN_TRANSIT -> DELIVERED (Order moves to DELIVERED)
        mvc.perform(withJson(patch("/api/v1/shipments/" + outboundShipmentId)
                .header("Authorization", bearer(admin)), Map.of(
                "status", "DELIVERED")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        // Customer requests return
        mvc.perform(withJson(post("/api/v1/orders/" + orderId + "/return")
                .header("Authorization", bearer(token)), Map.of("reason", "Book arrived damaged")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RETURN_REQUESTED"));

        // Admin approves return -> creates return shipment
        MvcResult retShipmentRes = mvc.perform(post("/api/v1/shipments/" + outboundShipmentId + "/return")
                .header("Authorization", bearer(admin)))
                .andExpect(status().isCreated())
                .andReturn();
        String returnShipmentId = json(retShipmentRes).get("id").asText();

        // Admin marks return shipment IN_TRANSIT then DELIVERED
        mvc.perform(withJson(patch("/api/v1/shipments/" + returnShipmentId)
                .header("Authorization", bearer(admin)), Map.of(
                "status", "IN_TRANSIT")))
                .andExpect(status().isOk());

        mvc.perform(withJson(patch("/api/v1/shipments/" + returnShipmentId)
                .header("Authorization", bearer(admin)), Map.of(
                "status", "DELIVERED")))
                .andExpect(status().isOk());

        // Order is now RETURNED and fully refunded
        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.refundedTotal").value(total));
    }
}
