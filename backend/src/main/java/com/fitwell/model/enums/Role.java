package com.fitwell.model.enums;

public enum Role {
    STUDENT,
    LAB,
    ADMIN;

    public static Role fromString(String role) {
        if (role == null) return null;
        try {
            return Role.valueOf(role.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

