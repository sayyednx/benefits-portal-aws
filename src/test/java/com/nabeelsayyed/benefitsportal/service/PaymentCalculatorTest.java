package com.nabeelsayyed.benefitsportal.service;

import com.nabeelsayyed.benefitsportal.model.CertificationOutcome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit tests: no Spring, no database. Each rule of one week's payment on its own. */
class PaymentCalculatorTest {

    private static final BigDecimal BENEFIT = new BigDecimal("400.00");
    private static final BigDecimal PLENTY_LEFT = new BigDecimal("10400.00");

    private final PaymentCalculator calculator = new PaymentCalculator(3, new BigDecimal("0.25"), true);

    private PaymentCalculator.WeekResult week(boolean able, boolean refused, int contacts, String earnings,
                                              BigDecimal remaining, boolean waitingServed) {
        return calculator.calculate(able, refused, contacts, new BigDecimal(earnings), BENEFIT, remaining, waitingServed);
    }

    @Test
    void aNormalWeekPaysTheFullBenefit() {
        var result = week(true, false, 3, "0", PLENTY_LEFT, true);
        assertThat(result.outcome()).isEqualTo(CertificationOutcome.PAID);
        assertThat(result.amount()).isEqualByComparingTo("400.00");
    }

    @Test
    void theFirstPayableWeekIsTheUnpaidWaitingWeek() {
        var result = week(true, false, 3, "0", PLENTY_LEFT, false);
        assertThat(result.outcome()).isEqualTo(CertificationOutcome.WAITING_WEEK);
        assertThat(result.amount()).isEqualByComparingTo("0");
    }

    @Test
    void notBeingAvailableForWorkMeansNoPay() {
        var result = week(false, false, 5, "0", PLENTY_LEFT, true);
        assertThat(result.outcome()).isEqualTo(CertificationOutcome.NOT_PAYABLE);
        assertThat(result.reason()).contains("available");
    }

    @Test
    void refusingAJobOfferMeansNoPay() {
        assertThat(week(true, true, 5, "0", PLENTY_LEFT, true).outcome())
                .isEqualTo(CertificationOutcome.NOT_PAYABLE);
    }

    @Test
    void tooFewJobContactsMeansNoPay() {
        var result = week(true, false, 2, "0", PLENTY_LEFT, true);
        assertThat(result.outcome()).isEqualTo(CertificationOutcome.NOT_PAYABLE);
        assertThat(result.reason()).contains("at least 3");
    }

    @Test
    void smallEarningsUnderTheDisregardDontReduceThePayment() {
        // 25% of 400 = 100 is ignored, so earning 80 changes nothing
        assertThat(week(true, false, 3, "80.00", PLENTY_LEFT, true).amount()).isEqualByComparingTo("400.00");
    }

    @Test
    void earningsAboveTheDisregardReduceThePaymentDollarForDollar() {
        // 160 earned - 100 ignored = 60 off, so 400 - 60 = 340
        var result = week(true, false, 3, "160.00", PLENTY_LEFT, true);
        assertThat(result.amount()).isEqualByComparingTo("340.00");
        assertThat(result.reason()).contains("Partial payment");
    }

    @Test
    void highEarningsMeanTheWeekIsNotPayable() {
        // 500 earned - 100 ignored = 400 off, which wipes out the 400 benefit
        assertThat(week(true, false, 3, "500.00", PLENTY_LEFT, true).outcome())
                .isEqualTo(CertificationOutcome.NOT_PAYABLE);
    }

    @Test
    void theLastPaymentIsCappedAtWhatIsLeft() {
        var result = week(true, false, 3, "0", new BigDecimal("150.00"), true);
        assertThat(result.amount()).isEqualByComparingTo("150.00");
        assertThat(result.reason()).contains("used up");
    }

    @Test
    void nothingIsPaidOnceTheBalanceIsUsedUp() {
        assertThat(week(true, false, 3, "0", BigDecimal.ZERO, true).outcome())
                .isEqualTo(CertificationOutcome.NOT_PAYABLE);
    }

    @Test
    void theWaitingWeekCanBeTurnedOff() {
        PaymentCalculator noWaitingWeek = new PaymentCalculator(3, new BigDecimal("0.25"), false);
        var result = noWaitingWeek.calculate(true, false, 3, BigDecimal.ZERO, BENEFIT, PLENTY_LEFT, false);
        assertThat(result.outcome()).isEqualTo(CertificationOutcome.PAID);
    }
}
