package com.fitwell.core.http;

public enum HttpMethod {
    GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD;

    public static HttpMethod fromString(String method) {
        if (method == null) {
            return GET;
        }
        try {
            return HttpMethod.valueOf(method.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return GET;
        }
    }
}

