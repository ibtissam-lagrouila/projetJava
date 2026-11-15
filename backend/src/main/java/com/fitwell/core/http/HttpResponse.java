package com.fitwell.core.http;

import com.fitwell.core.json.JsonCodec;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpResponse {

    private final HttpStatus status;
    private final Map<String, String> headers;
    private final byte[] body;

    public HttpResponse(HttpStatus status, Map<String, String> headers, byte[] body) {
        this.status = status;
        this.headers = new LinkedHashMap<>(headers);
        this.body = body != null ? body : new byte[0];
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public byte[] getBody() {
        return body;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static HttpResponse json(HttpStatus status, Object data) {
        byte[] bytes = JsonCodec.toJsonBytes(data);
        return builder()
                .status(status)
                .header("Content-Type", "application/json; charset=UTF-8")
                .body(bytes)
                .build();
    }

    public static HttpResponse ok(Object data) {
        return json(HttpStatus.OK, data);
    }

    public static HttpResponse created(Object data) {
        return json(HttpStatus.CREATED, data);
    }

    public static HttpResponse noContent() {
        return builder().status(HttpStatus.NO_CONTENT).build();
    }

    public static HttpResponse error(HttpStatus status, String message) {
        record ErrorResponse(int status, String error, String message) {}
        return json(status, new ErrorResponse(status.getCode(), status.getReasonPhrase(), message));
    }

    public static HttpResponse badRequest(String message) {
        return error(HttpStatus.BAD_REQUEST, message);
    }

    public static HttpResponse unauthorized(String message) {
        return error(HttpStatus.UNAUTHORIZED, message);
    }

    public static HttpResponse forbidden(String message) {
        return error(HttpStatus.FORBIDDEN, message);
    }

    public static HttpResponse notFound(String message) {
        return error(HttpStatus.NOT_FOUND, message);
    }

    public static HttpResponse internalError(String message) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public static class Builder {
        private HttpStatus status = HttpStatus.OK;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private byte[] body = new byte[0];

        public Builder status(HttpStatus status) {
            this.status = status;
            return this;
        }

        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public Builder setCookie(String name, String value, int maxAgeSeconds, boolean secure) {
            String cookie = String.format("%s=%s; Max-Age=%d; Path=/; HttpOnly; SameSite=Strict%s",
                    name, value, maxAgeSeconds, secure ? "; Secure" : "");
            this.headers.put("Set-Cookie", cookie);
            return this;
        }

        public Builder clearCookie(String name) {
            String cookie = String.format("%s=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict", name);
            this.headers.put("Set-Cookie", cookie);
            return this;
        }

        public Builder body(byte[] body) {
            this.body = body != null ? body : new byte[0];
            return this;
        }

        public Builder body(String text) {
            this.body = text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0];
            return this;
        }

        public HttpResponse build() {
            return new HttpResponse(status, headers, body);
        }
    }
}

