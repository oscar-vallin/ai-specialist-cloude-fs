package com.bookstore.ecommerce.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookstore.ecommerce.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemberApiTest extends IntegrationTest {

    private Map<String, Object> register(String email) {
        return Map.of("email", email, "password", "Passw0rd!x", "firstName", "Test", "lastName", "User");
    }

    private Map<String, Object> address(boolean isDefault) {
        return Map.ofEntries(Map.entry("firstName", "Test"), Map.entry("lastName", "User"), Map.entry("line1", "1 Main St"),
                Map.entry("city", "Austin"), Map.entry("state", "TX"), Map.entry("postalCode", "78701"),
                Map.entry("country", "US"), Map.entry("isDefault", isDefault));
    }

    @Test
    void registerLoginAndReadProfile() throws Exception {
        String email = "reg-" + UUID.randomUUID() + "@example.com";
        mvc.perform(withJson(post("/api/v1/auth/register"), register(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String token = login(email, "Passw0rd!x");
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        mvc.perform(get("/api/v1/users/me/wallet").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value("0.00"));
        mvc.perform(get("/api/v1/users/me/gift-points").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pointsPerCurrencyUnit").value(100));
    }

    @Test
    void passwordIsStoredAsBcrypt() throws Exception {
        String email = "bcrypt-" + UUID.randomUUID() + "@example.com";
        mvc.perform(withJson(post("/api/v1/auth/register"), register(email))).andExpect(status().isCreated());
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(hash).startsWith("$2").isNotEqualTo("Passw0rd!x");
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        mvc.perform(withJson(post("/api/v1/auth/register"), register(email))).andExpect(status().isCreated());
        mvc.perform(withJson(post("/api/v1/auth/register"), register(email.toUpperCase())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void registerValidatesInput() throws Exception {
        mvc.perform(withJson(post("/api/v1/auth/register"),
                Map.of("email", "not-an-email", "password", "short", "firstName", "", "lastName", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void unknownFieldsAreRejected() throws Exception {
        mvc.perform(withJson(post("/api/v1/auth/register"), Map.of("email", "a@b.co", "password", "Passw0rd!x",
                "firstName", "A", "lastName", "B", "roles", "ADMIN")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameError() throws Exception {
        mvc.perform(withJson(post("/api/v1/auth/login"), Map.of("email", CUSTOMER_EMAIL, "password", "wrong-pass")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(withJson(post("/api/v1/auth/login"), Map.of("email", "nobody@example.com", "password", "wrong-pass")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void disabledAccountCannotLogIn() throws Exception {
        String email = "disabled-" + UUID.randomUUID() + "@example.com";
        mvc.perform(withJson(post("/api/v1/auth/register"), register(email))).andExpect(status().isCreated());
        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE email = ?", email);
        mvc.perform(withJson(post("/api/v1/auth/login"), Map.of("email", email, "password", "Passw0rd!x")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void logoutRevokesTheToken() throws Exception {
        String token = customerToken();
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(token))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void invalidAndMissingTokensAreRejected() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void problemResponsesCarryRequestIdHeader() throws Exception {
        mvc.perform(get("/api/v1/users/me").header("X-Request-Id", "trace-123"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("X-Request-Id", "trace-123"))
                .andExpect(jsonPath("$.requestId").value("trace-123"));
    }

    @Test
    void addressLifecycleKeepsExactlyOneDefault() throws Exception {
        String email = "addr-" + UUID.randomUUID() + "@example.com";
        mvc.perform(withJson(post("/api/v1/auth/register"), register(email))).andExpect(status().isCreated());
        String auth = bearer(login(email, "Passw0rd!x"));

        String first = json(mvc.perform(withJson(post("/api/v1/users/me/addresses").header("Authorization", auth), address(false)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isDefault").value(true)).andReturn()).get("id").asText();
        String second = json(mvc.perform(withJson(post("/api/v1/users/me/addresses").header("Authorization", auth), address(true)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isDefault").value(true)).andReturn()).get("id").asText();

        mvc.perform(get("/api/v1/users/me/addresses").header("Authorization", auth))
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[1].isDefault").value(false));
        mvc.perform(post("/api/v1/users/me/addresses/" + first + "/default").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isDefault").value(true));
        Integer defaults = jdbc.queryForObject("SELECT count(*) FROM addresses a JOIN users u ON u.id = a.user_id "
                + "WHERE u.email = ? AND a.is_default", Integer.class, email);
        assertThat(defaults).isEqualTo(1);
        mvc.perform(delete("/api/v1/users/me/addresses/" + first).header("Authorization", auth)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/me/addresses/" + first).header("Authorization", auth))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
    }

    @Test
    void anotherUsersAddressIsNotFound() throws Exception {
        String auth = bearer(customerToken());
        String id = json(mvc.perform(get("/api/v1/users/me/addresses").header("Authorization", auth)).andReturn()).get(0).get("id").asText();
        String email = "other-" + UUID.randomUUID() + "@example.com";
        mvc.perform(withJson(post("/api/v1/auth/register"), register(email)));
        String other = bearer(login(email, "Passw0rd!x"));
        mvc.perform(get("/api/v1/users/me/addresses/" + id).header("Authorization", other))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
    }

    @Test
    void seededCustomerHasWalletAndGiftPoints() throws Exception {
        String auth = bearer(customerToken());
        mvc.perform(get("/api/v1/users/me/gift-points").header("Authorization", auth))
                .andExpect(jsonPath("$.balance").value(2000)).andExpect(jsonPath("$.currencyValue").value("20.00"));
        mvc.perform(get("/api/v1/users/me/wallet").header("Authorization", auth))
                .andExpect(jsonPath("$.balance").value("50.00"));
    }
}
