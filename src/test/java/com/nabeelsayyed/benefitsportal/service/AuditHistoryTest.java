package com.nabeelsayyed.benefitsportal.service;

import com.nabeelsayyed.benefitsportal.dto.ClaimDecisionRequest;
import com.nabeelsayyed.benefitsportal.dto.ClaimRequest;
import com.nabeelsayyed.benefitsportal.model.Claim;
import com.nabeelsayyed.benefitsportal.model.ClaimEvent;
import com.nabeelsayyed.benefitsportal.model.ClaimEventType;
import com.nabeelsayyed.benefitsportal.model.ClaimStatus;
import com.nabeelsayyed.benefitsportal.model.SeparationReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AuditHistoryTest {

    @Autowired
    private ClaimService claimService;

    @Autowired
    private AuditService auditService;

    private Claim fileClaim() {
        ClaimRequest request = new ClaimRequest();
        request.setClaimantName("Sam Lee");
        request.setClaimantEmail("sam.lee@example.com");
        request.setEmployerName("Riverside Foods");
        request.setLastDayWorked(LocalDate.now().minusDays(5));
        request.setSeparationReason(SeparationReason.LAID_OFF);
        request.setAverageWeeklyWage(new BigDecimal("700.00"));
        return claimService.fileClaim(request);
    }

    @Test
    void filingAClaimWritesAFiledEventByTheClaimant() {
        Claim claim = fileClaim();

        List<ClaimEvent> history = auditService.historyFor(claim.getId());

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getType()).isEqualTo(ClaimEventType.FILED);
        assertThat(history.get(0).getToStatus()).isEqualTo(ClaimStatus.FILED);
        assertThat(history.get(0).getActor()).isEqualTo("claimant");
    }

    @Test
    @WithMockUser(username = "pat.caseworker", roles = "ADMIN")
    void aDecisionRecordsWhoChangedWhatAndWhy() {
        Claim claim = fileClaim();
        ClaimDecisionRequest decision = new ClaimDecisionRequest();
        decision.setStatus(ClaimStatus.UNDER_REVIEW);
        decision.setReviewerNotes("Checking wages with employer");

        Claim decided = claimService.decide(claim.getId(), decision);
        List<ClaimEvent> history = auditService.historyFor(claim.getId());

        assertThat(decided.getReviewedBy()).isEqualTo("pat.caseworker");
        assertThat(history).hasSize(2);
        ClaimEvent change = history.get(1);
        assertThat(change.getType()).isEqualTo(ClaimEventType.STATUS_CHANGED);
        assertThat(change.getFromStatus()).isEqualTo(ClaimStatus.FILED);
        assertThat(change.getToStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(change.getActor()).isEqualTo("pat.caseworker");
        assertThat(change.getDetails()).isEqualTo("Checking wages with employer");
    }
}
