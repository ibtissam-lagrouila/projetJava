package com.fitwell.model.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum ApplicationStatus {
    DRAFT,
    SENT,
    REVIEWED,
    SHORTLISTED,
    INTERVIEW,
    DECISION_ACCEPTED,
    DECISION_REJECTED,
    WITHDRAWN;

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> STUDENT_TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(SENT, WITHDRAWN),
            SENT, EnumSet.of(WITHDRAWN),
            REVIEWED, EnumSet.of(WITHDRAWN),
            SHORTLISTED, EnumSet.of(WITHDRAWN),
            INTERVIEW, EnumSet.of(WITHDRAWN)
    );

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> LAB_TRANSITIONS = Map.of(
            SENT, EnumSet.of(REVIEWED, SHORTLISTED, INTERVIEW, DECISION_ACCEPTED, DECISION_REJECTED),
            REVIEWED, EnumSet.of(SHORTLISTED, INTERVIEW, DECISION_ACCEPTED, DECISION_REJECTED),
            SHORTLISTED, EnumSet.of(INTERVIEW, DECISION_ACCEPTED, DECISION_REJECTED),
            INTERVIEW, EnumSet.of(DECISION_ACCEPTED, DECISION_REJECTED)
    );

    public boolean canTransitionByStudent(ApplicationStatus newStatus) {
        if (newStatus == null || newStatus == this) return false;
        return STUDENT_TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(newStatus);
    }

    public boolean canTransitionByLab(ApplicationStatus newStatus) {
        if (newStatus == null || newStatus == this) return false;
        return LAB_TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(newStatus);
    }

    public static ApplicationStatus fromString(String status) {
        if (status == null) return null;
        try {
            return ApplicationStatus.valueOf(status.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

