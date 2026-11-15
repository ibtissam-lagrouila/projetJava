package com.fitwell;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HealthCheck {
    public static void main(String[] args) {
        String port = System.getenv().getOrDefault("PORT", "8080");
        String url = "http://localhost:" + port + "/api/health";
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                System.exit(0);
            } else {
                System.err.println("Healthcheck failed with HTTP status: " + response.statusCode());
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Healthcheck exception: " + e.getMessage());
            System.exit(1);
        }
    }
}

