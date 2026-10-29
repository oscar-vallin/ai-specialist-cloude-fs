package com.bookstore.ecommerce.support;

import com.bookstore.ecommerce.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class: real PostgreSQL (Testcontainers), the {@code local} profile (schema + seed) and a movable clock.
 * One container and one Spring context are shared by every subclass; tests that change data use their own users
 * and books, and the clock is reset before each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(IntegrationTest.ClockConfig.class)
public abstract class IntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    public static final String CUSTOMER_EMAIL = "customer@bookstore.local";
    public static final String CUSTOMER_PASSWORD = "CustomerLocal#2026";
    public static final String ADMIN_EMAIL = "admin@bookstore.local";
    public static final String ADMIN_PASSWORD = "AdminLocal#2026";

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Not a secret: it only signs tokens inside the test JVM.
        registry.add("app.jwt.secret", () -> "test-only-secret-test-only-secret-0123456789");
    }

    /** A second, primary {@link java.time.Clock} that the tests can move; every service picks it up. */
    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.now());
        }
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected ObjectMapper mapper;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected AppProperties props;

    @BeforeEach
    void resetClock() {
        clock.set(Instant.now());
    }

    // ----- helpers -----

    protected JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    protected MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
    }

    protected String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(withJson(MockMvcRequestBuilders.post("/api/v1/auth/login"),
                Map.of("email", email, "password", password))).andReturn();
        return json(result).get("accessToken").asText();
    }

    protected String customerToken() throws Exception {
        return login(CUSTOMER_EMAIL, CUSTOMER_PASSWORD);
    }

    protected String adminToken() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
