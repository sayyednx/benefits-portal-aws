package com.nabeelsayyed.benefitsportal.notification;

/**
 * "Something happened that the claimant should hear about." Services publish one of these;
 * NotificationService turns it into an email once the database change is saved for good.
 *
 * @param claimId which claim
 * @param kind    which email to send
 * @param extra   a detail for that email, such as the payment amount and week (may be null)
 */
public record ClaimNotificationEvent(Long claimId, Kind kind, String extra) {

    public enum Kind {
        CLAIM_FILED,
        DECISION_MADE,
        APPEAL_RECEIVED,
        PAYMENT_SENT
    }
}
