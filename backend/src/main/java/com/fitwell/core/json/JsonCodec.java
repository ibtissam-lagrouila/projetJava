package com.fitwell.core.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;

public final class JsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private JsonCodec() {}

    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize object to JSON: " + e.getMessage(), e);
        }
    }

    public static byte[] toJsonBytes(Object obj) {
        try {
            return MAPPER.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize object to JSON bytes: " + e.getMessage(), e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize JSON to " + clazz.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    public static <T> T fromJson(InputStream stream, Class<T> clazz) {
        try {
            return MAPPER.readValue(stream, clazz);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read JSON stream for " + clazz.getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}

