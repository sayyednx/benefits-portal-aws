package com.nabeelsayyed.benefitsportal.model;

/**
 * Lifecycle of a benefits claim, loosely modeled on how state unemployment-insurance
 * systems track a claim from submission to a paid/denied outcome.
 */
public enum ClaimStatus {
    FILED,
    UNDER_REVIEW,
    APPROVED,
    DENIED
}
