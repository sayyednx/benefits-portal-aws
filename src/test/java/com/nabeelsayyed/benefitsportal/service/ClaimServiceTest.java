package com.nabeelsayyed.benefitsportal.service;

import com.nabeelsayyed.benefitsportal.dto.ClaimRequest;
import com.nabeelsayyed.benefitsportal.model.Claim;
import com.nabeelsayyed.benefitsportal.model.ClaimStatus;
import com.nabeelsayyed.benefitsportal.model.SeparationReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ClaimServiceTest {

    @Autowired
    private ClaimService claimService;

    private ClaimRequest requestWith(SeparationReason reason, String weeklyWage) {
        ClaimRequest request = new ClaimRequest();
        request.setClaimantName("Jordan Rivera");
        request.setClaimantEmail("jordan.rivera@example.com");
        request.setEmployerName("Acme Logistics");
        request.setLastDayWorked(LocalDate.now().minusDays(10));
        request.setSeparationReason(reason);
        request.setAverageWeeklyWage(new BigDecimal(weeklyWage));
        return request;
    }

    @Test
    void filingAClaimDefaultsToFiledStatus() {
        Claim claim = claimService.fileClaim(requestWith(SeparationReason.LAID_OFF, "800.00"));

        assertThat(claim.getId()).isNotNull();
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.FILED);
    }

    @Test
    void layoffIsMonetarilyEligible() {
        assertThat(claimService.isMonetarilyEligible(SeparationReason.LAID_OFF)).isTrue();
    }

    @Test
    void quittingWithoutCauseIsNotEligible() {
        assertThat(claimService.isMonetarilyEligible(SeparationReason.QUIT_NO_CAUSE)).isFalse();
    }

    @Test
    void ineligibleClaimGetsZeroBenefitRegardlessOfWage() {
        Claim claim = claimService.fileClaim(requestWith(SeparationReason.FIRED_MISCONDUCT, "2000.00"));

        assertThat(claim.isEligible()).isFalse();
        assertThat(claim.getWeeklyBenefitAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void benefitIsHalfOfWageWhenBetweenMinAndMax() {
        // 800 * 0.5 = 400, which sits inside [50, 500]
        BigDecimal benefit = claimService.calculateWeeklyBenefit(new BigDecimal("800.00"));
        assertThat(benefit).isEqualByComparingTo("400.00");
    }

    @Test
    void benefitIsCappedAtMaxWeeklyBenefit() {
        // 2000 * 0.5 = 1000, which exceeds the 500 cap
        BigDecimal benefit = claimService.calculateWeeklyBenefit(new BigDecimal("2000.00"));
        assertThat(benefit).isEqualByComparingTo("500.00");
    }

    @Test
    void benefitIsFlooredAtMinWeeklyBenefit() {
        // 40 * 0.5 = 20, which is below the 50 floor
        BigDecimal benefit = claimService.calculateWeeklyBenefit(new BigDecimal("40.00"));
        assertThat(benefit).isEqualByComparingTo("50.00");
    }

    @Test
    void decidingAClaimUpdatesStatusAndReviewMetadata() {
        Claim filed = claimService.fileClaim(requestWith(SeparationReason.LAID_OFF, "600.00"));

        var decision = new com.nabeelsayyed.benefitsportal.dto.ClaimDecisionRequest();
        decision.setStatus(ClaimStatus.APPROVED);
        decision.setReviewerNotes("Verified with employer.");

        Claim decided = claimService.decide(filed.getId(), decision);

        assertThat(decided.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(decided.getReviewedAt()).isNotNull();
        assertThat(decided.getReviewerNotes()).isEqualTo("Verified with employer.");
    }
}
