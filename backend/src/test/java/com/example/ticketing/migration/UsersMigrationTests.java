package com.example.ticketing.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class UsersMigrationTests {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Test
    void preservesLegacyUserIdsAndRecordsWithoutEmailBasedIdentityLinking() {
        DataSource dataSource = dataSource();
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("1"))
            .load()
            .migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update(
            "INSERT INTO app_users (id, provider, email, name) VALUES (?, ?, ?, ?), (?, ?, ?, ?)",
            70L, "github", "legacy-github@example.test", "Legacy GitHub",
            80L, "google", "legacy-google@example.test", "Legacy Google"
        );

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();

        List<Map<String, Object>> users = jdbc.queryForList(
            "SELECT id, email, display_name, provider, provider_id FROM users ORDER BY id"
        );
        assertEquals(2, users.size());
        assertEquals(70L, ((Number) users.get(0).get("id")).longValue());
        assertEquals("legacy-github@example.test", users.get(0).get("email"));
        assertEquals("Legacy GitHub", users.get(0).get("display_name"));
        assertEquals("GITHUB", users.get(0).get("provider"));
        assertEquals("legacy-app-users:70", users.get(0).get("provider_id"));
        assertEquals("legacy-app-users:80", users.get(1).get("provider_id"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class));

        Long newUserId = jdbc.queryForObject(
            """
            INSERT INTO users (email, display_name, provider, provider_id)
            VALUES ('new@example.test', 'New User', 'GITHUB', 'github-subject')
            RETURNING id
            """,
            Long.class
        );
        assertTrue(newUserId > 80);
    }

    private DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }
}
