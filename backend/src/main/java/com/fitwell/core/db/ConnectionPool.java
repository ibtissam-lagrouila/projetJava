package com.fitwell.core.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ConnectionPool implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ConnectionPool.class);

    private final DatabaseConfig config;
    private final BlockingQueue<Connection> pool;
    private final AtomicInteger createdCount = new AtomicInteger(0);
    private volatile boolean isShutdown = false;

    public ConnectionPool(DatabaseConfig config) {
        this.config = config;
        this.pool = new ArrayBlockingQueue<>(config.maxPoolSize());
    }

    public void initialize() throws SQLException {
        log.info("Initializing Fitwell pure JDBC connection pool to {}", config.getJdbcUrl());
        // Pre-fill pool with minimum connections
        int initialSize = Math.min(3, config.maxPoolSize());
        for (int i = 0; i < initialSize; i++) {
            pool.offer(createNewConnection());
        }
        log.info("Connection pool initialized with {} initial connections (max: {})", initialSize, config.maxPoolSize());
    }

    public Connection acquire() throws SQLException {
        if (isShutdown) {
            throw new SQLException("Connection pool has been shut down");
        }

        Connection conn = pool.poll();
        if (conn == null) {
            if (createdCount.get() < config.maxPoolSize()) {
                conn = createNewConnection();
            } else {
                try {
                    conn = pool.poll(5, TimeUnit.SECONDS);
                    if (conn == null) {
                        throw new SQLException("Timed out waiting for an available database connection");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Interrupted waiting for database connection", e);
                }
            }
        }

        // Validate connection health
        if (!isConnectionValid(conn)) {
            closeSilently(conn);
            createdCount.decrementAndGet();
            conn = createNewConnection();
        }

        return conn;
    }

    public void release(Connection connection) {
        if (connection == null) return;
        if (isShutdown) {
            closeSilently(connection);
            return;
        }

        try {
            if (!connection.isClosed() && connection.isValid(1)) {
                if (!connection.getAutoCommit()) {
                    connection.setAutoCommit(true);
                }
                if (!pool.offer(connection)) {
                    // Pool was full
                    closeSilently(connection);
                    createdCount.decrementAndGet();
                }
            } else {
                closeSilently(connection);
                createdCount.decrementAndGet();
            }
        } catch (SQLException e) {
            closeSilently(connection);
            createdCount.decrementAndGet();
        }
    }

    private Connection createNewConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(config.getJdbcUrl(), config.user(), config.password());
        createdCount.incrementAndGet();
        return conn;
    }

    private boolean isConnectionValid(Connection conn) {
        try {
            return conn != null && !conn.isClosed() && conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private void closeSilently(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {}
        }
    }

    @Override
    public void close() {
        isShutdown = true;
        log.info("Closing connection pool...");
        Connection conn;
        while ((conn = pool.poll()) != null) {
            closeSilently(conn);
        }
        log.info("Connection pool closed");
    }
}

