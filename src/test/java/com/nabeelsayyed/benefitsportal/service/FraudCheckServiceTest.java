package com.nabeelsayyed.benefitsportal.service;

import com.nabeelsayyed.benefitsportal.dto.ClaimDecisionRequest;
import com.nabeelsayyed.benefitsportal.dto.ClaimRequest;
import com.nabeelsayyed.benefitsportal.exception.InvalidClaimActionException;
import com.nabeelsayyed.benefitsportal.model.*;
import com.nabeelsayyed.benefitsportal.support.LoginAs;
import com.nabeelsayyed.benefitsportal.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class FraudCheckServiceTest {

    @Autowired
    private ClaimService claimService;

    @Autowired
    private FraudCheckService fraudCheckService;

    @Autowired
    private AuditService auditService;

    private Claim file(String name, String email, String employer, String wage) {
        ClaimRequest request = new ClaimRequest();
        request.setClaimantName(name);
        request.setClaimantEmail(email);
        request.setEmployerName(employer);
        request.setLastDayWorked(LocalDate.now().minusDays(6));
        request.setSeparationReason(SeparationReason.LAID_OFF);
        request.setAverageWeeklyWage(new BigDecimal(wage));
        return claimService.fileClaim(request);
    }

    private Claim file(String tag, String wage) {
        return file("Avery Stone " + tag, "avery+" + tag + "@example.com", "Coastal Freight " + tag, wage);
    }

    private void move(Claim claim, ClaimStatus to, String notes) {
        ClaimDecisionRequest decision = new ClaimDecisionRequest();
        decision.setStatus(to);
        decision.setReviewerNotes(notes);
        LoginAs.caseworker("casey", () -> claimService.decide(claim.getId(), decision));
    }

    @Test
    void anOrdinaryClaimIsNotFlagged() {
        Claim claim = file(TestData.unique(), "750.00");

        assertThat(fraudCheckService.flagsFor(claim.getId())).isEmpty();
    }

    @Test
    void aSecondOpenClaimWithTheSameEmailIsFlagged() {
        String tag = TestData.unique();
        Claim first = file("Avery Stone " + tag, "AVERY+" + tag + "@example.com", "Coastal Freight", "700.00");
        Claim second = file("Avery Stone " + tag, "avery+" + tag + "@example.com", "Other Employer", "700.00");

        assertThat(fraudCheckService.flagsFor(first.getId())).isEmpty();
        assertThat(fraudCheckService.flagsFor(second.getId()))
                .extracting(ClaimFlag::getReason)
                .containsExactly(FlagReason.DUPLICATE_OPEN_CLAIM);
        assertThat(fraudCheckService.flagsFor(second.getId()).get(0).getDetails()).contains("#" + first.getId());
    }

    @Test
    void aDeniedEarlierClaimDoesNotCountAsOpen() {
        String tag = TestData.unique();
        Claim first = file("Avery Stone " + tag, "avery+" + tag + "@example.com", "Coastal Freight", "700.00");
        move(first, ClaimStatus.UNDER_REVIEW, null);
        move(first, ClaimStatus.DENIED, "Wrong state");

        Claim second = file("Avery Stone " + tag, "avery+" + tag + "@example.com", "Coastal Freight", "700.00");

        assertThat(fraudCheckService.flagsFor(second.getId())).isEmpty();
    }

    @Test
    void theSameNameAndEmployerUnderANewEmailIsFlagged() {
        String tag = TestData.unique();
        file("Avery Stone " + tag, "avery+" + tag + "@example.com", "Coastal Freight " + tag, "700.00");
        Claim copy = file("avery stone " + tag, "different+" + tag + "@example.com", "COASTAL FREIGHT " + tag, "700.00");

        assertThat(fraudCheckService.flagsFor(copy.getId()))
                .extracting(ClaimFlag::getReason)
                .containsExactly(FlagReason.POSSIBLE_IDENTITY_REUSE);
    }

    @Test
    void anUnusuallyHighWageIsFlagged() {
        Claim claim = file(TestData.unique(), "9000.00");

        assertThat(fraudCheckService.flagsFor(claim.getId()))
                .extracting(ClaimFlag::getReason)
                .containsExactly(FlagReason.HIGH_WAGE);
    }

    @Test
    void aFlaggedClaimCantBeApprovedUntilTheFlagIsCleared() {
        Claim claim = file(TestData.unique(), "9000.00");
        move(claim, ClaimStatus.UNDER_REVIEW, null);

        assertThatThrownBy(() -> move(claim, ClaimStatus.APPROVED, null))
                .isInstanceOf(InvalidClaimActionException.class)
                .hasMessageContaining("fraud flags");

        ClaimFlag flag = fraudCheckService.flagsFor(claim.getId()).get(0);
        LoginAs.caseworker("casey", () ->
                fraudCheckService.resolve(claim.getId(), flag.getId(), "Called employer; salary confirmed."));
        move(claim, ClaimStatus.APPROVED, null);

        ClaimFlag cleared = fraudCheckService.flagsFor(claim.getId()).get(0);
        assertThat(cleared.isOpen()).isFalse();
        assertThat(cleared.getResolvedBy()).isEqualTo("casey");
        assertThat(claimService.findById(claim.getId()).getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(auditService.historyFor(claim.getId()))
                .extracting(ClaimEvent::getType)
                .contains(ClaimEventType.FLAG_RAISED, ClaimEventType.FLAG_CLEARED);
    }

    @Test
    void clearingAFlagNeedsANote() {
        Claim claim = file(TestData.unique(), "9000.00");
        ClaimFlag flag = fraudCheckService.flagsFor(claim.getId()).get(0);

        assertThatThrownBy(() -> fraudCheckService.resolve(claim.getId(), flag.getId(), " "))
                .isInstanceOf(InvalidClaimActionException.class);
    }

    @Test
    void aFlaggedClaimCanStillBeDenied() {
        Claim claim = file(TestData.unique(), "9000.00");
        move(claim, ClaimStatus.UNDER_REVIEW, null);
        move(claim, ClaimStatus.DENIED, "Wage could not be verified.");

        assertThat(claimService.findById(claim.getId()).getStatus()).isEqualTo(ClaimStatus.DENIED);
    }
}
