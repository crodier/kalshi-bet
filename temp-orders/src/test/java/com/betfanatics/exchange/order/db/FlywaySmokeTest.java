package com.betfanatics.exchange.order.db;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Disabled;

@Disabled
@Testcontainers
public class FlywaySmokeTest {

    @Container
    private static final PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:17");

    @Test
    void testFlywayMigrations() {
        // Configure Flyway using Testcontainers
        Flyway flyway =
                Flyway.configure()
                        .dataSource(
                                postgreSQLContainer.getJdbcUrl(),
                                postgreSQLContainer.getUsername(),
                                postgreSQLContainer.getPassword())
                        .locations("classpath:db/migration")
                        .baselineOnMigrate(true)
                        .placeholders(Map.of("ENVIRONMENT", "local"))
                        .load();

        // Run migrations
        flyway.migrate();

        // Perform smoke tests (basic checks)
        smokeTestTablesExist(flyway);
        smokeTestDataInserted(flyway, "inventory");
    }

    private void smokeTestTablesExist(Flyway flyway) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(flyway.getConfiguration().getDataSource());
        List<String> tableNames =
                jdbcTemplate.queryForList(
                        "SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname != 'pg_catalog' AND schemaname != 'information_schema'",
                        String.class);

        assertTrue(tableNames.size() > 0, "No tables found after migration.");
    }

    private void smokeTestDataInserted(Flyway flyway, String tableName) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(flyway.getConfiguration().getDataSource());

        // Check if the specified table has at least one record.
        try {
            String sql = "SELECT * FROM " + tableName + " LIMIT 1";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            assertTrue(
                    results.size() > 0, "No data found in '" + tableName + "' table after migration.");
        } catch (org.springframework.dao.DataAccessException e) {
            // Handle if the table does not exist.
            assertTrue(
                    false,
                    "The '" + tableName + "' table does not exist. Flyway migrations did not run correctly.");
        }
    }
}
