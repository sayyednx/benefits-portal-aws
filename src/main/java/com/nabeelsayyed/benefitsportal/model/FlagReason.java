package com.nabeelsayyed.benefitsportal.model;

/** Why the automatic checks put a claim on hold for a closer look. */
public enum FlagReason {
    DUPLICATE_OPEN_CLAIM("Same email already has an open claim"),
    POSSIBLE_IDENTITY_REUSE("Same name and employer as an open claim under a different email"),
    HIGH_WAGE("Reported weekly wage is unusually high");

    private final String label;

    FlagReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
