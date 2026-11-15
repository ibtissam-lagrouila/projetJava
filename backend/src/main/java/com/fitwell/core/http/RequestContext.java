package com.fitwell.core.http;

import com.fitwell.core.json.JsonCodec;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class RequestContext {

    private final HttpExchange exchange;
    private final HttpMethod method;
    private final String path;
    private final Map<String, String> pathParams;
    private final Map<String, List<String>> queryParams;
    private final Map<String, String> cookies;
    private final Map<String, Object> attributes = new HashMap<>();
    private final Set<String> trustedProxies;
    private byte[] cachedBody;

    public RequestContext(HttpExchange exchange, Map<String, String> pathParams, Set<String> trustedProxies) {
        this.exchange = exchange;
        this.method = HttpMethod.fromString(exchange.getRequestMethod());
        URI requestURI = exchange.getRequestURI();
        this.path = requestURI.getPath();
        this.pathParams = pathParams != null ? new HashMap<>(pathParams) : new HashMap<>();
        this.queryParams = parseQueryParams(requestURI.getRawQuery());
        this.cookies = parseCookies(exchange.getRequestHeaders().getFirst("Cookie"));
        this.trustedProxies = trustedProxies != null ? trustedProxies : Collections.emptySet();
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getPathParam(String name) {
        return pathParams.get(name);
    }

    public void setPathParam(String name, String value) {
        pathParams.put(name, value);
    }

    public String getQueryParam(String name) {
        List<String> values = queryParams.get(name);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    public List<String> getQueryParams(String name) {
        return queryParams.getOrDefault(name, Collections.emptyList());
    }

    public String getHeader(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    public String getCookie(String name) {
        return cookies.get(name);
    }

    public String getClientIp() {
        String remoteAddress = exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null
                ? exchange.getRemoteAddress().getAddress().getHostAddress()
                : "127.0.0.1";

        // Only trust X-Forwarded-For if request actually comes from a trusted proxy
        boolean isTrusted = isTrustedProxy(remoteAddress);
        if (isTrusted) {
            String forwardedFor = getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String[] parts = forwardedFor.split(",");
                return parts[0].trim();
            }
            String realIp = getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return remoteAddress;
    }

    private boolean isTrustedProxy(String ip) {
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "localhost".equalsIgnoreCase(ip)) {
            return true;
        }
        for (String trusted : trustedProxies) {
            if (trusted.equals(ip)) {
                return true;
            }
            // Simple CIDR / subnet match for 172.16.0.0/12 or 172.18.0.0/16
            if (trusted.contains("/") && ip.startsWith(trusted.substring(0, trusted.indexOf('.')))) {
                return true;
            }
        }
        return false;
    }

    public byte[] getBody(int maxBytes) throws IOException {
        if (cachedBody != null) {
            return cachedBody;
        }
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = is.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IllegalArgumentException("Payload size exceeds maximum allowed limit of " + maxBytes + " bytes");
                }
                os.write(buffer, 0, read);
            }
            cachedBody = os.toByteArray();
            return cachedBody;
        }
    }

    public byte[] getBody() throws IOException {
        return getBody(5 * 1024 * 1024); // 5MB default limit
    }

    public String getBodyAsString() throws IOException {
        return new String(getBody(), StandardCharsets.UTF_8);
    }

    public <T> T parseJsonBody(Class<T> clazz) throws IOException {
        byte[] body = getBody();
        if (body.length == 0) {
            throw new IllegalArgumentException("Request body is empty");
        }
        return JsonCodec.fromJson(new String(body, StandardCharsets.UTF_8), clazz);
    }

    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name) {
        return (T) attributes.get(name);
    }

    public HttpExchange getExchange() {
        return exchange;
    }

    private static Map<String, List<String>> parseQueryParams(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            String key;
            String val = "";
            if (idx > 0) {
                key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            } else {
                key = URLDecoder.decode(pair, StandardCharsets.UTF_8);
            }
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(val);
        }
        return result;
    }

    private static Map<String, String> parseCookies(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, String> cookies = new LinkedHashMap<>();
        String[] rawCookies = cookieHeader.split(";");
        for (String raw : rawCookies) {
            String trimmed = raw.trim();
            int eq = trimmed.indexOf("=");
            if (eq > 0) {
                String name = trimmed.substring(0, eq).trim();
                String val = trimmed.substring(eq + 1).trim();
                if (val.startsWith("\"") && val.endsWith("\"") && val.length() > 1) {
                    val = val.substring(1, val.length() - 1);
                }
                cookies.put(name, val);
            }
        }
        return cookies;
    }
}

