package com.nabeelsayyed.benefitsportal.service;

import com.nabeelsayyed.benefitsportal.model.CertificationOutcome;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Decides how much one week pays. It only does math on the answers it's given (no database,
 * no clock), which makes every rule easy to test on its own.
 *
 * The rules, checked in this order, follow how most state programs work:
 * 1. You must have been able to work and available for work.
 * 2. You must not have turned down a suitable job offer.
 * 3. You must have contacted enough employers (the "work search" rule).
 * 4. Part-time earnings reduce the payment, but a small amount is ignored (the "disregard")
 *    so taking a little part-time work never leaves you worse off.
 * 5. The first payable week is an unpaid "waiting week".
 * 6. Total payments can't go over the claim's maximum (weekly benefit x max weeks).
 */
@Component
public class PaymentCalculator {

    private final int minJobContacts;
    private final BigDecimal earningsDisregardRate;
    private final boolean waitingWeekEnabled;

    public PaymentCalculator(@Value("${benefits.min-job-contacts:3}") int minJobContacts,
                             @Value("${benefits.earnings-disregard-rate:0.25}") BigDecimal earningsDisregardRate,
                             @Value("${benefits.waiting-week:true}") boolean waitingWeekEnabled) {
        this.minJobContacts = minJobContacts;
        this.earningsDisregardRate = earningsDisregardRate;
        this.waitingWeekEnabled = waitingWeekEnabled;
    }

    /** The result for one week: what happened, how much, and a plain-English reason. */
    public record WeekResult(CertificationOutcome outcome, BigDecimal amount, String reason) {
        static WeekResult notPayable(String reason) {
            return new WeekResult(CertificationOutcome.NOT_PAYABLE, BigDecimal.ZERO.setScale(2), reason);
        }
    }

    public WeekResult calculate(boolean ableAndAvailable, boolean refusedWork, int jobContacts,
                                BigDecimal grossEarnings, BigDecimal weeklyBenefit,
                                BigDecimal remainingBalance, boolean waitingWeekAlreadyServed) {
        if (!ableAndAvailable) {
            return WeekResult.notPayable("You must be able to work and available for work to be paid for a week.");
        }
        if (refusedWork) {
            return WeekResult.notPayable("Turning down a suitable job offer makes the week not payable.");
        }
        if (jobContacts < minJobContacts) {
            return WeekResult.notPayable("You need at least " + minJobContacts
                    + " job contacts each week. You reported " + jobContacts + ".");
        }

        BigDecimal amount = afterEarnings(weeklyBenefit, grossEarnings);
        if (amount.signum() == 0) {
            return WeekResult.notPayable("Your earnings this week were too high for a partial payment.");
        }

        if (remainingBalance.signum() <= 0) {
            return WeekResult.notPayable("You have received the maximum benefits for this claim.");
        }

        if (waitingWeekEnabled && !waitingWeekAlreadyServed) {
            return new WeekResult(CertificationOutcome.WAITING_WEEK, BigDecimal.ZERO.setScale(2),
                    "This is your unpaid waiting week. Your next payable week will be paid.");
        }

        if (amount.compareTo(remainingBalance) > 0) {
            return new WeekResult(CertificationOutcome.PAID, remainingBalance.setScale(2, RoundingMode.HALF_UP),
                    "Paid the rest of your remaining balance. Your benefits for this claim are now used up.");
        }
        String reason = grossEarnings.signum() > 0
                ? "Partial payment: reduced because of $" + grossEarnings.setScale(2, RoundingMode.HALF_UP) + " in earnings."
                : "Full weekly benefit paid.";
        return new WeekResult(CertificationOutcome.PAID, amount, reason);
    }

    /**
     * weekly benefit - (earnings - disregard), never below zero.
     * Example with a $400 benefit: the disregard is 25% = $100. Earning $160 reduces the
     * payment by $60, so the claimant gets $340 and keeps their $160.
     */
    BigDecimal afterEarnings(BigDecimal weeklyBenefit, BigDecimal grossEarnings) {
        BigDecimal disregard = weeklyBenefit.multiply(earningsDisregardRate);
        BigDecimal deduction = grossEarnings.subtract(disregard).max(BigDecimal.ZERO);
        return weeklyBenefit.subtract(deduction).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
