package com.nabeelsayyed.benefitsportal.service;

import com.nabeelsayyed.benefitsportal.dto.CertificationRequest;
import com.nabeelsayyed.benefitsportal.dto.ClaimDecisionRequest;
import com.nabeelsayyed.benefitsportal.dto.ClaimRequest;
import com.nabeelsayyed.benefitsportal.exception.InvalidClaimActionException;
import com.nabeelsayyed.benefitsportal.model.CertificationOutcome;
import com.nabeelsayyed.benefitsportal.model.Claim;
import com.nabeelsayyed.benefitsportal.model.ClaimStatus;
import com.nabeelsayyed.benefitsportal.model.SeparationReason;
import com.nabeelsayyed.benefitsportal.support.MutableClock;
import com.nabeelsayyed.benefitsportal.support.TestClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The weekly certification flow with a real database and a test clock.
 * Claims are filed on Monday, March 2, 2026, so the first week ends Saturday, March 7.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class CertificationServiceTest {

    private static final LocalDate WEEK_1 = LocalDate.of(2026, 3, 7);
    private static final LocalDate WEEK_2 = LocalDate.of(2026, 3, 14);
    private static final LocalDate WEEK_3 = LocalDate.of(2026, 3, 21);

    @Autowired
    private ClaimService claimService;

    @Autowired
    private CertificationService certificationService;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.setTo(LocalDateTime.of(2026, 3, 2, 9, 0));
    }

    private Claim fileClaim() {
        ClaimRequest request = new ClaimRequest();
        request.setClaimantName("Jesse Park");
        request.setClaimantEmail("jesse.park@example.com");
        request.setEmployerName("Summit Manufacturing");
        request.setLastDayWorked(LocalDate.of(2026, 2, 27));
        request.setSeparationReason(SeparationReason.LAID_OFF);
        request.setAverageWeeklyWage(new BigDecimal("800.00")); // weekly benefit $400
        return claimService.fileClaim(request);
    }

    private Claim approvedClaim() {
        Claim claim = fileClaim();
        for (ClaimStatus to : new ClaimStatus[]{ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED}) {
            ClaimDecisionRequest decision = new ClaimDecisionRequest();
            decision.setStatus(to);
            claimService.decide(claim.getId(), decision);
        }
        return claimService.findById(claim.getId());
    }

    private CertificationRequest answers(LocalDate week, int contacts, String earnings) {
        CertificationRequest request = new CertificationRequest();
        request.setWeekEnding(week);
        request.setAbleAndAvailable(true);
        request.setRefusedWork(false);
        request.setJobContacts(contacts);
        request.setGrossEarnings(new BigDecimal(earnings));
        return request;
    }

    @Test
    void theMaximumBenefitIs26Weeks() {
        assertThat(certificationService.maximumBenefit(approvedClaim())).isEqualByComparingTo("10400.00");
    }

    @Test
    void finishedWeeksShowUpAsDueOldestFirst() {
        Claim claim = approvedClaim();
        clock.setTo(LocalDateTime.of(2026, 3, 16, 9, 0)); // Monday after week 2

        assertThat(certificationService.weeksDue(claim)).containsExactly(WEEK_1, WEEK_2);
    }

    @Test
    void aClaimThatIsNotApprovedHasNoWeeksDue() {
        Claim claim = fileClaim();
        clock.setTo(LocalDateTime.of(2026, 3, 16, 9, 0));

        assertThat(certificationService.weeksDue(claim)).isEmpty();
        assertThatThrownBy(() -> certificationService.certify(claim.getId(), answers(WEEK_1, 3, "0")))
                .isInstanceOf(InvalidClaimActionException.class)
                .hasMessageContaining("approved");
    }

    @Test
    void waitingWeekThenPaidWeeksThenTheLedgerAddsUp() {
        Claim claim = approvedClaim();
        clock.setTo(LocalDateTime.of(2026, 3, 23, 9, 0));

        var first = certificationService.certify(claim.getId(), answers(WEEK_1, 3, "0"));
        var second = certificationService.certify(claim.getId(), answers(WEEK_2, 4, "0"));
        var third = certificationService.certify(claim.getId(), answers(WEEK_3, 3, "160.00"));

        assertThat(first.getOutcome()).isEqualTo(CertificationOutcome.WAITING_WEEK);
        assertThat(second.getOutcome()).isEqualTo(CertificationOutcome.PAID);
        assertThat(second.getAmountPayable()).isEqualByComparingTo("400.00");
        assertThat(third.getAmountPayable()).isEqualByComparingTo("340.00");
        assertThat(certificationService.paymentsFor(claim.getId())).hasSize(2);
        assertThat(certificationService.totalPaid(claim.getId())).isEqualByComparingTo("740.00");
        assertThat(certificationService.remainingBalance(claim)).isEqualByComparingTo("9660.00");
        assertThat(certificationService.weeksDue(claim)).isEmpty();
    }

    @Test
    void aWeekThatIsNotPayableDoesNotUseUpTheWaitingWeek() {
        Claim claim = approvedClaim();
        clock.setTo(LocalDateTime.of(2026, 3, 16, 9, 0));

        var first = certificationService.certify(claim.getId(), answers(WEEK_1, 1, "0"));
        var second = certificationService.certify(claim.getId(), answers(WEEK_2, 3, "0"));

        assertThat(first.getOutcome()).isEqualTo(CertificationOutcome.NOT_PAYABLE);
        assertThat(second.getOutcome()).isEqualTo(CertificationOutcome.WAITING_WEEK);
    }

    @Test
    void aWeekCanOnlyBeCertifiedOnce() {
        Claim claim = approvedClaim();
        clock.setTo(LocalDateTime.of(2026, 3, 9, 9, 0));
        certificationService.certify(claim.getId(), answers(WEEK_1, 3, "0"));

        assertThatThrownBy(() -> certificationService.certify(claim.getId(), answers(WEEK_1, 3, "0")))
                .isInstanceOf(InvalidClaimActionException.class)
                .hasMessageContaining("already certified");
    }

    @Test
    void aWeekThatHasNotEndedCantBeCertified() {
        Claim claim = approvedClaim();
        clock.setTo(LocalDateTime.of(2026, 3, 6, 9, 0)); // Friday of week 1

        assertThatThrownBy(() -> certificationService.certify(claim.getId(), answers(WEEK_1, 3, "0")))
                .isInstanceOf(InvalidClaimActionException.class)
                .hasMessageContaining("after it has ended");
    }

    @Test
    void theWeekEndingDateMustBeASaturday() {
        Claim claim = approvedClaim();
        clock.setTo(LocalDateTime.of(2026, 3, 16, 9, 0));

        assertThatThrownBy(() -> certificationService.certify(claim.getId(), answers(LocalDate.of(2026, 3, 10), 3, "0")))
                .isInstanceOf(InvalidClaimActionException.class)
                .hasMessageContaining("Saturday");
    }
}
