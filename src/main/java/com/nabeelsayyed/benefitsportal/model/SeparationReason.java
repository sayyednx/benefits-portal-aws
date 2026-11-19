package com.nabeelsayyed.benefitsportal.model;

/**
 * Why the claimant is no longer working. Real state UI systems use this to decide
 * eligibility (e.g. a voluntary quit without cause is usually disqualifying) — this
 * app keeps that logic simple, but the field is here so the eligibility rule has
 * something real to look at.
 */
public enum SeparationReason {
    LAID_OFF("Laid off (lack of work)",
            "Your employer let you go because there wasn't enough work, or your job was cut."),
    COMPANY_CLOSED("My employer closed or went out of business",
            "The business, store, or location you worked at shut down."),
    FIRED_NOT_MISCONDUCT("Fired, but not for misconduct",
            "For example, you weren't a good fit or couldn't meet a quota, but you didn't break a rule on purpose."),
    FIRED_MISCONDUCT("Fired for misconduct",
            "You were fired for breaking a work rule on purpose, like stealing or not showing up without calling."),
    QUIT_WITH_CAUSE("Quit for a good reason",
            "For example, unsafe working conditions, harassment, or your pay or hours were cut a lot."),
    QUIT_NO_CAUSE("Quit for a personal reason",
            "You left by choice for a reason not caused by the job.");

    private final String label;
    private final String hint;

    SeparationReason(String label, String hint) {
        this.label = label;
        this.hint = hint;
    }

    /** Plain-English name shown in forms and tables. */
    public String getLabel() {
        return label;
    }

    /** One-sentence help text so claimants can pick the right reason. */
    public String getHint() {
        return hint;
    }
}
