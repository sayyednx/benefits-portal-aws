package com.nabeelsayyed.benefitsportal.controller;

import com.nabeelsayyed.benefitsportal.dto.ClaimDecisionRequest;
import com.nabeelsayyed.benefitsportal.dto.ClaimRequest;
import com.nabeelsayyed.benefitsportal.model.Claim;
import com.nabeelsayyed.benefitsportal.model.ClaimStatus;
import com.nabeelsayyed.benefitsportal.model.SeparationReason;
import com.nabeelsayyed.benefitsportal.service.ClaimService;
import com.nabeelsayyed.benefitsportal.support.MutableClock;
import com.nabeelsayyed.benefitsportal.support.TestClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The weekly certification form on the claimant page, and the payments table on the staff page. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class CertificationPagesTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private MutableClock clock;

    private Claim approvedClaim() {
        clock.setTo(LocalDateTime.of(2026, 3, 2, 9, 0));
        ClaimRequest request = new ClaimRequest();
        request.setClaimantName("Quinn Foster");
        request.setClaimantEmail("quinn@example.com");
        request.setEmployerName("Bayview Hotel");
        request.setLastDayWorked(LocalDate.of(2026, 2, 27));
        request.setSeparationReason(SeparationReason.LAID_OFF);
        request.setAverageWeeklyWage(new BigDecimal("600.00"));
        Claim claim = claimService.fileClaim(request);
        for (ClaimStatus to : new ClaimStatus[]{ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED}) {
            ClaimDecisionRequest decision = new ClaimDecisionRequest();
            decision.setStatus(to);
            claimService.decide(claim.getId(), decision);
        }
        return claim;
    }

    private MockHttpSession claimantSession(Claim claim) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ClaimantController.VERIFIED_CLAIM, claim.getId());
        return session;
    }

    @Test
    void anApprovedClaimantCanCertifyAFinishedWeek() throws Exception {
        Claim claim = approvedClaim();
        clock.setTo(LocalDateTime.of(2026, 3, 16, 9, 0));
        MockHttpSession session = claimantSession(claim);

        mvc.perform(get("/status/claim").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Weekly certification")))
                .andExpect(content().string(containsString("You have 2 weeks to certify")));

        mvc.perform(post("/status/certify").session(session).with(csrf())
                        .param("weekEnding", "2026-03-07")
                        .param("ableAndAvailable", "true")
                        .param("refusedWork", "false")
                        .param("jobContacts", "3")
                        .param("grossEarnings", "0"))
                .andExpect(redirectedUrl("/status/claim"))
                .andExpect(flash().attribute("message", containsString("waiting week")));
    }

    @Test
    void missingAnswersComeBackAsAMessage() throws Exception {
        Claim claim = approvedClaim();
        clock.setTo(LocalDateTime.of(2026, 3, 16, 9, 0));

        mvc.perform(post("/status/certify").session(claimantSession(claim)).with(csrf())
                        .param("weekEnding", "2026-03-07"))
                .andExpect(redirectedUrl("/status/claim"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void staffSeeTheCertificationsAndBalance() throws Exception {
        Claim claim = approvedClaim();
        mvc.perform(get("/admin/claims/" + claim.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Weekly certifications and payments")))
                .andExpect(content().string(containsString("$7800.00")));
    }
}
