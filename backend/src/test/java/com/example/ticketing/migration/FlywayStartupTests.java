package com.example.ticketing.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.cloud.vault.enabled=false",
    "spring.cloud.vault.token=test-vault-token",
    "spring.sql.init.mode=never",
    "oauth.github.client-id=test-github-client",
    "oauth.github.client-secret=test-github-secret",
    "oauth.google.client-id=test-google-client",
    "oauth.google.client-secret=test-google-secret",
    "security.jwt-secret=01234567890123456789012345678901"
})
@Testcontainers(disabledWithoutDocker = true)
class FlywayStartupTests {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void appliesUserMigrationAutomaticallyAtStartup() {
        assertEquals(
            "users",
            jdbcTemplate.queryForObject("SELECT to_regclass('public.users')::text", String.class)
        );
        assertEquals(
            "2",
            jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE version = '2'",
                String.class
            )
        );
    }
}
