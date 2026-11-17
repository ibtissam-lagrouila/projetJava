package com.fitwell.core.http;

import java.util.Collections;
import java.util.Map;

@SuppressWarnings("serial")
public final class ValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final Map<String, String> errors;

    public ValidationException(String message, Map<String, String> errors) {
        super(message);
        this.errors = errors != null ? Collections.unmodifiableMap(errors) : Collections.emptyMap();
    }

    public ValidationException(String field, String error) {
        this("Validation failed", Map.of(field, error));
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}

