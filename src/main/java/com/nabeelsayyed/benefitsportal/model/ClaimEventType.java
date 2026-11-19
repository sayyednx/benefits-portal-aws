package com.nabeelsayyed.benefitsportal.model;

/** The kinds of things that can happen to a claim, as recorded in its audit history. */
public enum ClaimEventType {
    FILED("Claim filed", true),
    STATUS_CHANGED("Status changed", true),
    APPEAL_FILED("Appeal filed", true),
    CERTIFICATION_SUBMITTED("Weekly check-in submitted", true),
    PAYMENT_ISSUED("Payment sent", true),
    DOCUMENT_UPLOADED("Document uploaded", true),
    // Fraud flags are for staff only. Showing them to the claimant would tip off real fraud.
    FLAG_RAISED("Fraud flag raised", false),
    FLAG_CLEARED("Fraud flag cleared", false);

    private final String label;
    private final boolean visibleToClaimant;

    ClaimEventType(String label, boolean visibleToClaimant) {
        this.label = label;
        this.visibleToClaimant = visibleToClaimant;
    }

    public String getLabel() {
        return label;
    }

    public boolean isVisibleToClaimant() {
        return visibleToClaimant;
    }
}
