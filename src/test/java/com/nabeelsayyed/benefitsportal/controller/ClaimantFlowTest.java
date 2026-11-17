package com.nabeelsayyed.benefitsportal.controller;

import com.nabeelsayyed.benefitsportal.dto.ClaimDecisionRequest;
import com.nabeelsayyed.benefitsportal.dto.ClaimRequest;
import com.nabeelsayyed.benefitsportal.model.Claim;
import com.nabeelsayyed.benefitsportal.model.ClaimStatus;
import com.nabeelsayyed.benefitsportal.model.SeparationReason;
import com.nabeelsayyed.benefitsportal.service.ClaimService;
import com.nabeelsayyed.benefitsportal.support.LoginAs;
import com.nabeelsayyed.benefitsportal.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The claimant's "check my claim" page and the appeal form, through real HTTP requests. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClaimantFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private com.nabeelsayyed.benefitsportal.service.DocumentService documentService;

    private Claim fileClaim(String email) {
        ClaimRequest request = new ClaimRequest();
        request.setClaimantName("Taylor Brooks " + TestData.unique());
        request.setClaimantEmail(email);
        request.setEmployerName("Northside Retail");
        request.setLastDayWorked(LocalDate.now().minusDays(7));
        request.setSeparationReason(SeparationReason.LAID_OFF);
        request.setAverageWeeklyWage(new BigDecimal("640.00"));
        return claimService.fileClaim(request);
    }

    /** Logs the claimant in with their number and email, and returns their session. */
    private MockHttpSession verify(Claim claim, String email) throws Exception {
        MvcResult result = mvc.perform(post("/status").with(csrf())
                        .param("confirmationNumber", claim.getConfirmationNumber().toLowerCase())
                        .param("email", email.toUpperCase()))
                .andExpect(redirectedUrl("/status/claim"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    @Test
    void filingShowsARandomConfirmationNumber() throws Exception {
        mvc.perform(post("/claims").with(csrf())
                        .param("claimantName", "Casey Nguyen")
                        .param("claimantEmail", "casey@example.com")
                        .param("employerName", "Metro Transit")
                        .param("lastDayWorked", LocalDate.now().minusDays(2).toString())
                        .param("separationReason", "LAID_OFF")
                        .param("averageWeeklyWage", "720.00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your confirmation number")))
                .andExpect(content().string(containsString("UI-")));
    }

    @Test
    void confirmationNumbersLookRightAndAreUnique() {
        Claim first = fileClaim("one@example.com");
        Claim second = fileClaim("two@example.com");

        assertThat(first.getConfirmationNumber()).matches("UI-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}");
        assertThat(first.getConfirmationNumber()).isNotEqualTo(second.getConfirmationNumber());
    }

    @Test
    void theRightNumberAndEmailShowTheClaim() throws Exception {
        Claim claim = fileClaim("jamie@example.com");
        MockHttpSession session = verify(claim, "jamie@example.com");

        mvc.perform(get("/status/claim").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(claim.getConfirmationNumber())))
                .andExpect(content().string(containsString(ClaimStatus.FILED.getDescription())));
    }

    @Test
    void aWrongEmailGetsTheSameVagueMessage() throws Exception {
        Claim claim = fileClaim("riley@example.com");

        mvc.perform(post("/status").with(csrf())
                        .param("confirmationNumber", claim.getConfirmationNumber())
                        .param("email", "someone.else@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("couldn&#39;t find a claim")));
    }

    @Test
    void theStatusPageNeedsAVerifiedSession() throws Exception {
        mvc.perform(get("/status/claim"))
                .andExpect(redirectedUrl("/status"));
    }

    @Test
    void aDeniedClaimantSeesWhyAndCanAppeal() throws Exception {
        Claim claim = fileClaim("drew@example.com");
        ClaimDecisionRequest review = new ClaimDecisionRequest();
        review.setStatus(ClaimStatus.UNDER_REVIEW);
        LoginAs.caseworker("casey", () -> claimService.decide(claim.getId(), review));
        ClaimDecisionRequest deny = new ClaimDecisionRequest();
        deny.setStatus(ClaimStatus.DENIED);
        deny.setReviewerNotes("Employer says you were offered continued work.");
        LoginAs.caseworker("casey", () -> claimService.decide(claim.getId(), deny));

        MockHttpSession session = verify(claim, "drew@example.com");
        mvc.perform(get("/status/claim").session(session))
                .andExpect(content().string(containsString("Employer says you were offered continued work.")))
                .andExpect(content().string(containsString("Appeal this decision")));

        mvc.perform(post("/status/appeal").session(session).with(csrf())
                        .param("reason", "The offer was for a different city."))
                .andExpect(redirectedUrl("/status/claim"));

        assertThat(claimService.findById(claim.getId()).getStatus()).isEqualTo(ClaimStatus.APPEALED);
        mvc.perform(get("/status/claim").session(session))
                .andExpect(content().string(not(containsString("Appeal this decision"))));
    }

    @Test
    void aClaimantCanUploadAndStaffCanDownload() throws Exception {
        Claim claim = fileClaim("morgan@example.com");
        MockHttpSession session = verify(claim, "morgan@example.com");
        byte[] pdf = "%PDF-1.4 test".getBytes();

        mvc.perform(multipart("/status/documents").file(new MockMultipartFile("file", "stub.pdf", "application/pdf", pdf))
                        .param("type", "PAY_STUB").session(session).with(csrf()))
                .andExpect(redirectedUrl("/status/claim"))
                .andExpect(flash().attribute("message", containsString("stub.pdf")));

        mvc.perform(get("/status/claim").session(session))
                .andExpect(content().string(containsString("stub.pdf")));
    }

    @Test
    @WithMockUser(roles = "CASEWORKER")
    void staffDownloadsComeBackAsAnAttachment() throws Exception {
        Claim claim = fileClaim("ari@example.com");
        var doc = documentService.upload(claim.getId(), com.nabeelsayyed.benefitsportal.model.DocumentType.PAY_STUB,
                new MockMultipartFile("file", "stub.pdf", "application/pdf", "%PDF-1.4 test".getBytes()));

        mvc.perform(get("/admin/claims/" + claim.getId() + "/documents/" + doc.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void theDownloadLinkIsStaffOnly() throws Exception {
        Claim claim = fileClaim("kai@example.com");
        mvc.perform(get("/admin/claims/" + claim.getId() + "/documents/1"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void claimantsDontSeeFraudFlagsInTheirHistory() throws Exception {
        ClaimRequest request = new ClaimRequest();
        request.setClaimantName("Harper Lane " + TestData.unique());
        request.setClaimantEmail("harper@example.com");
        request.setEmployerName("Pinnacle Bank");
        request.setLastDayWorked(LocalDate.now().minusDays(7));
        request.setSeparationReason(SeparationReason.LAID_OFF);
        request.setAverageWeeklyWage(new BigDecimal("9500.00"));
        Claim claim = claimService.fileClaim(request);

        mvc.perform(get("/status/claim").session(verify(claim, "harper@example.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("FLAG"))));
    }
}
