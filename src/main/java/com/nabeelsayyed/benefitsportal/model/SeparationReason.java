package com.nabeelsayyed.benefitsportal.model;

/**
 * Why the claimant is no longer working. Real state UI systems use this to decide
 * eligibility (e.g. a voluntary quit without cause is usually disqualifying) — this
 * app keeps that logic simple, but the field is here so the eligibility rule has
 * something real to look at.
 */
public enum SeparationReason {
    LAID_OFF,
    COMPANY_CLOSED,
    FIRED_NOT_MISCONDUCT,
    FIRED_MISCONDUCT,
    QUIT_WITH_CAUSE,
    QUIT_NO_CAUSE
}
