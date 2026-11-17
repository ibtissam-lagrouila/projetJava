package com.fitwell.core.security;

import com.fitwell.model.entity.User;
import com.fitwell.model.enums.LabRole;
import com.fitwell.model.enums.Role;

import java.util.UUID;

public record AuthContext(
        User user,
        UUID labId,
        LabRole labRole
) {
    public UUID getUserId() {
        return user.id();
    }

    public Role getRole() {
        return user.role();
    }

    public boolean isStudent() {
        return user.role() == Role.STUDENT;
    }

    public boolean isLab() {
        return user.role() == Role.LAB;
    }

    public boolean isAdmin() {
        return user.role() == Role.ADMIN;
    }

    public boolean hasLabRole(LabRole requiredRole) {
        return labRole != null && labRole == requiredRole;
    }
}

