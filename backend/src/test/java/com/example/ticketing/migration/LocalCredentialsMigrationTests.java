package com.example.ticketing.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class LocalCredentialsMigrationTests {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Test
    void addsLocalPasswordsWithoutTouchingExistingAccounts() {
        DataSource dataSource = dataSource();
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("2"))
            .load()
            .migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update(
            "INSERT INTO users (id, email, display_name, provider, provider_id) VALUES (?, ?, ?, ?, ?)",
            5L, "legacy@example.test", "Legacy", "GOOGLE", "google-subject"
        );

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();

        List<Map<String, Object>> users = jdbc.queryForList("SELECT provider, provider_id, password_hash FROM users");
        assertEquals(1, users.size());
        assertEquals("GOOGLE", users.get(0).get("provider"));
        assertNull(users.get(0).get("password_hash"));

        jdbc.update(
            "INSERT INTO users (email, display_name, provider, provider_id, password_hash) "
                + "VALUES ('dana@example.test', 'Dana', 'EMAIL', 'dana@example.test', 'bcrypt-hash')"
        );
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE provider = 'EMAIL' AND password_hash IS NOT NULL",
                Integer.class
            )
        );

        assertThrows(DataAccessException.class, () -> jdbc.update(
            "INSERT INTO users (email, display_name, provider, provider_id) "
                + "VALUES ('other@example.test', 'Other', 'FACEBOOK', 'facebook-subject')"
        ));

        assertThrows(DataAccessException.class, () -> jdbc.update(
            "INSERT INTO users (email, display_name, provider, provider_id, password_hash) "
                + "VALUES ('DANA@example.test', 'Dana Again', 'EMAIL', 'dana@example.test', 'bcrypt-hash')"
        ));

        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
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
