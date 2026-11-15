package com.fitwell.model.enums;

public enum LabRole {
    HR,
    CEO,
    SUPERVISOR;

    public static LabRole fromString(String role) {
        if (role == null) return null;
        try {
            return LabRole.valueOf(role.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

