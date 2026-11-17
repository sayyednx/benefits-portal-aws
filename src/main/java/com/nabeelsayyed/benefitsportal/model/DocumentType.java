package com.nabeelsayyed.benefitsportal.model;

/** Kinds of proof a claimant can upload. */
public enum DocumentType {
    PAY_STUB("Pay stub"),
    SEPARATION_LETTER("Layoff or separation letter"),
    PHOTO_ID("Photo ID"),
    OTHER("Something else");

    private final String label;

    DocumentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
