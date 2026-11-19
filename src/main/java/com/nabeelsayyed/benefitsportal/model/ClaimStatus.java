package com.nabeelsayyed.benefitsportal.model;

import java.util.Set;

/**
 * Lifecycle of a benefits claim, loosely modeled on how state unemployment-insurance
 * systems track a claim from submission to a paid/denied outcome.
 *
 * <pre>
 *   FILED ──▶ UNDER_REVIEW ──▶ APPROVED
 *                    │
 *                    └──────▶ DENIED ──(claimant appeals)──▶ APPEALED ──▶ APPROVED
 *                                                                  └────▶ DENIED (final)
 * </pre>
 *
 * The allowed moves live here, next to the statuses themselves, so the rule is written
 * down in exactly one place and both the web pages and the API obey it.
 */
public enum ClaimStatus {
    FILED("Filed", 1,
            "We received your claim. It is waiting for a staff member to pick it up."),
    UNDER_REVIEW("Under review", 2,
            "A staff member is checking the details of your claim."),
    APPROVED("Approved", 4,
            "Your claim is approved. Check in each week to get paid."),
    DENIED("Denied", 3,
            "Your claim was denied. You can appeal this decision if you think it is wrong."),
    APPEALED("Appeal in review", 3,
            "You appealed the decision. A different reviewer will look at your claim again.");

    private final String label;
    private final int progressStep;
    private final String description;

    ClaimStatus(String label, int progressStep, String description) {
        this.label = label;
        this.progressStep = progressStep;
        this.description = description;
    }

    /** Short display name, like "Under review". */
    public String getLabel() {
        return label;
    }

    /**
     * Where this status sits on the claimant's progress bar:
     * 1 Filed, 2 Under review, 3 Decision, 4 Get paid weekly.
     */
    public int getProgressStep() {
        return progressStep;
    }

    /** A plain-English explanation of this status, shown to the claimant. */
    public String getDescription() {
        return description;
    }

    /**
     * The statuses a staff member may move a claim to from this one. Moving a claim to
     * APPEALED isn't in here on purpose: only the claimant can start an appeal.
     */
    public Set<ClaimStatus> staffCanMoveTo() {
        return switch (this) {
            case FILED -> Set.of(UNDER_REVIEW);
            case UNDER_REVIEW, APPEALED -> Set.of(APPROVED, DENIED);
            case APPROVED, DENIED -> Set.of();
        };
    }

    /** True once a decision has been made and no staff action is left. */
    public boolean isFinalForStaff() {
        return staffCanMoveTo().isEmpty();
    }
}
