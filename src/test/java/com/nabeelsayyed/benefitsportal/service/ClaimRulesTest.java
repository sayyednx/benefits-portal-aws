package com.nabeelsayyed.benefitsportal.service;

import com.nabeelsayyed.benefitsportal.dto.ClaimDecisionRequest;
import com.nabeelsayyed.benefitsportal.dto.ClaimRequest;
import com.nabeelsayyed.benefitsportal.exception.InvalidClaimActionException;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The status rules (which moves are allowed) and the appeal rules. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class ClaimRulesTest {

    @Autowired
    private ClaimService claimService;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.setTo(LocalDateTime.of(2026, 3, 2, 9, 0));
    }

    private Claim file(SeparationReason reason) {
        ClaimRequest request = new ClaimRequest();
        request.setClaimantName("Morgan Diaz");
        request.setClaimantEmail("morgan.diaz@example.com");
        request.setEmployerName("Lakeside Hospital");
        request.setLastDayWorked(LocalDate.of(2026, 2, 27));
        request.setSeparationReason(reason);
        request.setAverageWeeklyWage(new BigDecimal("800.00"));
        return claimService.fileClaim(request);
    }

    private Claim move(Claim claim, ClaimStatus to, String notes) {
        ClaimDecisionRequest decision = new ClaimDecisionRequest();
        decision.setStatus(to);
        decision.setReviewerNotes(notes);
        return claimService.decide(claim.getId(), decision);
    }

    private Claim deniedClaim() {
        Claim claim = file(SeparationReason.LAID_OFF);
        move(claim, ClaimStatus.UNDER_REVIEW, null);
        return move(claim, ClaimStatus.DENIED, "Employer reports the claimant quit.");
    }

    @Test
    void aClaimCantSkipReview() {
        Claim claim = file(SeparationReason.LAID_OFF);

        assertThatThrownBy(() -> move(claim, ClaimStatus.APPROVED, null))
                .isInstanceOf(InvalidClaimActionException.class)
                .hasMessageContaining("can't be moved to APPROVED");
    }

    @Test
    void staffCantMoveAClaimToAppealed() {
        Claim claim = file(SeparationReason.LAID_OFF);
        move(claim, ClaimStatus.UNDER_REVIEW, null);

        assertThatThrownBy(() -> move(claim, ClaimStatus.APPEALED, null))
                .isInstanceOf(InvalidClaimActionException.class);
    }

    @Test
    void aDenialMustExplainWhy() {
        Claim claim = file(SeparationReason.LAID_OFF);
        move(claim, ClaimStatus.UNDER_REVIEW, null);

        assertThatThrownBy(() -> move(claim, ClaimStatus.DENIED, "  "))
                .isInstanceOf(InvalidClaimActionException.class)
                .hasMessageContaining("why");
    }

    @Test
    void aDeniedClaimCanBeAppealedWithinTheWindow() {
        Claim denied = deniedClaim();
        clock.advance(Duration.ofDays(29));

        Claim appealed = claimService.fileAppeal(denied.getId(), "I was laid off, here is my letter.");

        assertThat(appealed.getStatus()).isEqualTo(ClaimStatus.APPEALED);
        assertThat(appealed.getAppealReason()).isEqualTo("I was laid off, here is my letter.");
        assertThat(appealed.getAppealedAt()).isNotNull();
    }

    @Test
    void anAppealAfterTheDeadlineIsRefused() {
        Claim denied = deniedClaim();
        clock.advance(Duration.ofDays(31));

        assertThat(claimService.canAppeal(denied)).isFalse();
        assertThatThrownBy(() -> claimService.fileAppeal(denied.getId(), "Late, sorry"))
                .isInstanceOf(InvalidClaimActionException.class)
                .hasMessageContaining("deadline");
    }

    @Test
    void aClaimCanOnlyBeAppealedOnce() {
        Claim denied = deniedClaim();
        claimService.fileAppeal(denied.getId(), "First appeal");
        Claim deniedAgain = move(denied, ClaimStatus.DENIED, "Decision upheld on appeal.");

        assertThat(claimService.canAppeal(deniedAgain)).isFalse();
        assertThatThrownBy(() -> claimService.fileAppeal(denied.getId(), "Second appeal"))
                .isInstanceOf(InvalidClaimActionException.class)
                .hasMessageContaining("already been appealed");
    }

    @Test
    void anApprovedClaimCantBeAppealed() {
        Claim claim = file(SeparationReason.LAID_OFF);
        move(claim, ClaimStatus.UNDER_REVIEW, null);
        move(claim, ClaimStatus.APPROVED, null);

        assertThatThrownBy(() -> claimService.fileAppeal(claim.getId(), "Why not"))
                .isInstanceOf(InvalidClaimActionException.class);
    }

    @Test
    void approvingAnIneligibleClaimOnAppealRecalculatesTheBenefit() {
        Claim claim = file(SeparationReason.QUIT_NO_CAUSE);
        assertThat(claim.getWeeklyBenefitAmount()).isEqualByComparingTo("0");
        move(claim, ClaimStatus.UNDER_REVIEW, null);
        move(claim, ClaimStatus.DENIED, "Quit without good cause.");
        claimService.fileAppeal(claim.getId(), "My employer cut my hours in half, that is good cause.");

        Claim approved = move(claim, ClaimStatus.APPROVED, "Hours cut counts as good cause.");

        assertThat(approved.isEligible()).isTrue();
        assertThat(approved.getWeeklyBenefitAmount()).isEqualByComparingTo("400.00");
    }
}
