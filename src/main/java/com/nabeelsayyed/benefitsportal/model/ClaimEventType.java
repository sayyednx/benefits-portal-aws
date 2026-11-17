package com.nabeelsayyed.benefitsportal.model;

/** The kinds of things that can happen to a claim, as recorded in its audit history. */
public enum ClaimEventType {
    FILED(true),
    STATUS_CHANGED(true),
    APPEAL_FILED(true),
    CERTIFICATION_SUBMITTED(true),
    PAYMENT_ISSUED(true),
    DOCUMENT_UPLOADED(true),
    // Fraud flags are for staff only. Showing them to the claimant would tip off real fraud.
    FLAG_RAISED(false),
    FLAG_CLEARED(false);

    private final boolean visibleToClaimant;

    ClaimEventType(boolean visibleToClaimant) {
        this.visibleToClaimant = visibleToClaimant;
    }

    public boolean isVisibleToClaimant() {
        return visibleToClaimant;
    }
}
