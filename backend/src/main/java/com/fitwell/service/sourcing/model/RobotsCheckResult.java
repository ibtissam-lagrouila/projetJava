package com.fitwell.service.sourcing.model;

public record RobotsCheckResult(
        boolean allowed,
        int crawlDelaySeconds,
        String message,
        String robotsStatus
) {
    public static RobotsCheckResult allow(int crawlDelay, String message) {
        return new RobotsCheckResult(true, crawlDelay, message, "ALLOWED");
    }

    public static RobotsCheckResult disallow(String message) {
        return new RobotsCheckResult(false, 0, message, "DISALLOWED");
    }

    public static RobotsCheckResult error(String errorMsg) {
        return new RobotsCheckResult(false, 0, errorMsg, "ERROR");
    }
}
