package com.fitwell.core.db;

public record DatabaseConfig(
        String host,
        int port,
        String database,
        String user,
        String password,
        int maxPoolSize
) {
    public static DatabaseConfig fromEnv() {
        String host = getEnvOrDefault("DB_HOST", "localhost");
        int port = Integer.parseInt(getEnvOrDefault("DB_PORT", "5432"));
        String database = getEnvOrDefault("DB_NAME", "fitwell");
        String user = getEnvOrDefault("DB_USER", "fitwell_user");
        String password = getEnvOrDefault("DB_PASSWORD", "fitwell_secret");
        int maxPoolSize = Integer.parseInt(getEnvOrDefault("DB_MAX_POOL_SIZE", "15"));
        return new DatabaseConfig(host, port, database, user, password, maxPoolSize);
    }

    public String getJdbcUrl() {
        return String.format("jdbc:postgresql://%s:%d/%s?ApplicationName=FitwellJavaSE", host, port, database);
    }

    private static String getEnvOrDefault(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val.trim() : defaultValue;
    }
}

