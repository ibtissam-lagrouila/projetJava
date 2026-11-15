package com.fitwell.core.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class Migrator {

    private static final Logger log = LoggerFactory.getLogger(Migrator.class);

    private final ConnectionPool pool;

    public Migrator(ConnectionPool pool) {
        this.pool = pool;
    }

    public void migrate() throws SQLException {
        log.info("Running database migrations...");
        Connection conn = pool.acquire();
        try {
            ensureSchemaVersionTable(conn);
            List<Integer> appliedVersions = getAppliedVersions(conn);

            // Discover and apply migrations
            List<MigrationScript> scripts = List.of(
                    new MigrationScript(1, "V001__initial_schema.sql", "/db/migrations/V001__initial_schema.sql"),
                    new MigrationScript(2, "V002__onboarding_state.sql", "/db/migrations/V002__onboarding_state.sql")
            );

            for (MigrationScript script : scripts) {
                if (!appliedVersions.contains(script.version())) {
                    applyMigration(conn, script);
                } else {
                    log.debug("Migration V{} ({}) already applied", script.version(), script.name());
                }
            }
            log.info("All database migrations applied successfully");
        } finally {
            pool.release(conn);
        }
    }

    private void ensureSchemaVersionTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INT PRIMARY KEY,
                    script VARCHAR(255) NOT NULL,
                    installed_on TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                )
            """);
        }
    }

    private List<Integer> getAppliedVersions(Connection conn) throws SQLException {
        List<Integer> versions = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version ORDER BY version ASC")) {
            while (rs.next()) {
                versions.add(rs.getInt("version"));
            }
        }
        return versions;
    }

    private void applyMigration(Connection conn, MigrationScript script) throws SQLException {
        log.info("Applying migration V{} - {}...", script.version(), script.name());
        String sql = loadSqlFromClasspath(script.path());

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO schema_version (version, script, installed_on) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
                ps.setInt(1, script.version());
                ps.setString(2, script.name());
                ps.executeUpdate();
            }

            conn.commit();
            log.info("Successfully applied migration V{} - {}", script.version(), script.name());
        } catch (SQLException e) {
            conn.rollback();
            log.error("Failed to apply migration V{} - {}: {}", script.version(), script.name(), e.getMessage());
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    private String loadSqlFromClasspath(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Migration resource not found on classpath: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Error reading migration script from classpath: " + path, e);
        }
    }

    private record MigrationScript(int version, String name, String path) {}
}

