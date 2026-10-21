package com.bookstore.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test against a real PostgreSQL: the application starts, Flyway applies V1 and
 * Hibernate validation passes (ddl-auto=validate), and the health endpoint is public.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BookstoreApplicationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void healthIsUpAndPublic() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedEndpointsReturn401WhenAnonymous() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/products"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void flywayAppliedV1() {
        String version = jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                String.class);
        assertThat(version).isEqualTo("1");
    }

    @Test
    void referenceRolesAreSeeded() {
        List<String> roles = jdbc.queryForList("SELECT name FROM roles ORDER BY name", String.class);
        assertThat(roles).containsExactly("ADMIN", "CUSTOMER", "GUEST");
    }

    @Test
    void everyTableHasUuidPrimaryKeyAndTimestamps() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                        + "AND table_name <> 'flyway_schema_history'",
                String.class);
        assertThat(tables).hasSize(37);
        for (String table : tables) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' "
                            + "AND table_name = ? AND ((column_name = 'id' AND data_type = 'uuid') "
                            + "OR (column_name IN ('created_at', 'updated_at') AND data_type = 'timestamp with time zone'))",
                    Integer.class, table);
            assertThat(count).as("id/created_at/updated_at on %s", table).isEqualTo(3);
        }
    }
}
