package com.nabeelsayyed.benefitsportal.controller;

import com.nabeelsayyed.benefitsportal.dto.ClaimRequest;
import com.nabeelsayyed.benefitsportal.model.Claim;
import com.nabeelsayyed.benefitsportal.model.SeparationReason;
import com.nabeelsayyed.benefitsportal.service.ClaimService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Renders the staff pages for real, so a broken template fails the build instead of a demo. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPagesTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ClaimService claimService;

    private Claim fileClaim() {
        ClaimRequest request = new ClaimRequest();
        request.setClaimantName("Alex Kim");
        request.setClaimantEmail("alex.kim@example.com");
        request.setEmployerName("Harbor Freight Lines");
        request.setLastDayWorked(LocalDate.now().minusDays(3));
        request.setSeparationReason(SeparationReason.COMPANY_CLOSED);
        request.setAverageWeeklyWage(new BigDecimal("900.00"));
        return claimService.fileClaim(request);
    }

    @Test
    void claimDetailPageNeedsALogin() throws Exception {
        Claim claim = fileClaim();
        mvc.perform(get("/admin/claims/" + claim.getId()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "CASEWORKER")
    void claimDetailPageShowsTheHistory() throws Exception {
        Claim claim = fileClaim();
        mvc.perform(get("/admin/claims/" + claim.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("History")))
                .andExpect(content().string(containsString("FILED")));
    }

    @Test
    @WithMockUser(roles = "CASEWORKER")
    void historyApiReturnsTheEventsAsJson() throws Exception {
        Claim claim = fileClaim();
        mvc.perform(get("/api/claims/" + claim.getId() + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("FILED"))
                .andExpect(jsonPath("$[0].toStatus").value("FILED"));
    }

    @Test
    @WithMockUser(roles = "CASEWORKER")
    void queuePageRenders() throws Exception {
        fileClaim();
        mvc.perform(get("/admin/claims"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Claims queue")));
    }

    @Test
    @WithMockUser(roles = "CASEWORKER")
    void aBrokenRuleOnTheWebPageShowsAMessageInsteadOfAnErrorPage() throws Exception {
        Claim claim = fileClaim();
        mvc.perform(post("/admin/claims/" + claim.getId() + "/decision").with(csrf())
                        .param("status", "APPROVED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", containsString("can't be moved")));
    }

    @Test
    @WithMockUser(roles = "CASEWORKER")
    void aBrokenRuleOnTheApiReturns409() throws Exception {
        Claim claim = fileClaim();
        mvc.perform(patch("/api/claims/" + claim.getId() + "/status")
                        .contentType("application/json")
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("can't be moved")));
    }
}
