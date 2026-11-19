package com.nabeelsayyed.benefitsportal.model;

/** What happened to one week's certification. */
public enum CertificationOutcome {
    /** The week is payable and a payment was issued. */
    PAID("Paid"),
    /** The week would be payable, but it's the claim's one unpaid "waiting week". */
    WAITING_WEEK("Waiting week"),
    /** The week isn't payable (for example, not enough job contacts). */
    NOT_PAYABLE("Not payable");

    private final String label;

    CertificationOutcome(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
