package com.nabeelsayyed.benefitsportal.notification;

import com.nabeelsayyed.benefitsportal.dto.CertificationRequest;
import com.nabeelsayyed.benefitsportal.dto.ClaimDecisionRequest;
import com.nabeelsayyed.benefitsportal.dto.ClaimRequest;
import com.nabeelsayyed.benefitsportal.model.Claim;
import com.nabeelsayyed.benefitsportal.model.ClaimStatus;
import com.nabeelsayyed.benefitsportal.model.SeparationReason;
import com.nabeelsayyed.benefitsportal.service.CertificationService;
import com.nabeelsayyed.benefitsportal.service.ClaimService;
import com.nabeelsayyed.benefitsportal.support.LoginAs;
import com.nabeelsayyed.benefitsportal.support.MutableClock;
import com.nabeelsayyed.benefitsportal.support.TestClockConfig;
import com.nabeelsayyed.benefitsportal.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Walks a claim through its life and checks the claimant gets the right email at each step.
 * The real mail sender is swapped for a fake one that just records what it was asked to send.
 * Emails go out in the background, so each check waits up to 5 seconds for the email.
 */
@SpringBootTest(properties = "notifications.enabled=true")
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class NotificationFlowTest {

    @MockBean
    private EmailSender emailSender;

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

    private Claim fileClaim(String email, String wage) {
        ClaimRequest request = new ClaimRequest();
        String tag = TestData.unique();
        request.setClaimantName("Jesse Park " + tag);
        request.setClaimantEmail(email);
        request.setEmployerName("Summit Manufacturing " + tag);
        request.setLastDayWorked(LocalDate.of(2026, 2, 27));
        request.setSeparationReason(SeparationReason.LAID_OFF);
        request.setAverageWeeklyWage(new BigDecimal(wage));
        return claimService.fileClaim(request);
    }

    private void move(Claim claim, ClaimStatus to, String notes) {
        ClaimDecisionRequest decision = new ClaimDecisionRequest();
        decision.setStatus(to);
        decision.setReviewerNotes(notes);
        LoginAs.caseworker("casey", () -> claimService.decide(claim.getId(), decision));
    }

    /** Waits for the email with this subject start and returns its plain-text body. */
    private String emailTo(String email, String subjectStart) {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(5000)).send(eq(email), startsWith(subjectStart), anyString(), text.capture());
        return text.getValue();
    }

    @Test
    void filingAClaimSendsAConfirmationWithTheNumber() {
        String email = "jesse+" + TestData.unique() + "@example.com";
        Claim claim = fileClaim(email, "800.00");

        String text = emailTo(email, "We received your unemployment claim");
        assertThat(text).contains(claim.getConfirmationNumber()).contains("$400.00")
                .contains("Hi Jesse").contains("Nabeel Sayyed");
    }

    @Test
    void approvalAndPaymentEachSendAnEmail() {
        String email = "jesse+" + TestData.unique() + "@example.com";
        Claim claim = fileClaim(email, "800.00");
        move(claim, ClaimStatus.UNDER_REVIEW, null);
        move(claim, ClaimStatus.APPROVED, null);

        assertThat(emailTo(email, "Your unemployment claim is approved")).contains("Weekly benefit: $400.00");

        // Two weeks later: week 1 is the unpaid waiting week, week 2 pays.
        clock.setTo(LocalDateTime.of(2026, 3, 16, 9, 0));
        certificationService.certify(claim.getId(), answers(LocalDate.of(2026, 3, 7)));
        certificationService.certify(claim.getId(), answers(LocalDate.of(2026, 3, 14)));

        assertThat(emailTo(email, "Your weekly benefit payment was sent"))
                .contains("$400.00 for the week ending March 14, 2026");
    }

    @Test
    void denialExplainsHowToAppealAndAppealIsConfirmed() {
        String email = "jesse+" + TestData.unique() + "@example.com";
        Claim claim = fileClaim(email, "800.00");
        move(claim, ClaimStatus.UNDER_REVIEW, null);
        move(claim, ClaimStatus.DENIED, "Employer says you quit.");

        assertThat(emailTo(email, "A decision on your unemployment claim"))
                .contains("Reason: Employer says you quit.")
                .contains("You have until April 1, 2026 to appeal.");

        claimService.fileAppeal(claim.getId(), "I was laid off, here is my letter.");
        assertThat(emailTo(email, "We received your appeal")).contains(claim.getConfirmationNumber());
    }

    @Test
    void movingToUnderReviewSendsNothing() {
        String email = "jesse+" + TestData.unique() + "@example.com";
        Claim claim = fileClaim(email, "800.00");
        emailTo(email, "We received your unemployment claim");
        move(claim, ClaimStatus.UNDER_REVIEW, null);

        verify(emailSender, after(500).times(1)).send(eq(email), anyString(), anyString(), anyString());
    }

    @Test
    void aBrokenMailServerDoesNotStopTheClaimBeingSaved() {
        String email = "jesse+" + TestData.unique() + "@example.com";
        doThrow(new RuntimeException("mail server down"))
                .when(emailSender).send(eq(email), anyString(), anyString(), anyString());

        Claim claim = fileClaim(email, "800.00");

        verify(emailSender, timeout(5000)).send(eq(email), anyString(), anyString(), anyString());
        assertThat(claimService.findById(claim.getId()).getStatus()).isEqualTo(ClaimStatus.FILED);
    }

    @Test
    void theHtmlEmailShowsTheHighlightsAndButton() {
        String email = "jesse+" + TestData.unique() + "@example.com";
        Claim claim = fileClaim(email, "800.00");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(5000)).send(eq(email), anyString(), html.capture(), anyString());
        assertThat(html.getValue())
                .contains(claim.getConfirmationNumber())
                .contains("href=\"http://localhost:8080/status\"")
                .contains("Check my claim");
    }

    private CertificationRequest answers(LocalDate week) {
        CertificationRequest request = new CertificationRequest();
        request.setWeekEnding(week);
        request.setAbleAndAvailable(true);
        request.setRefusedWork(false);
        request.setJobContacts(3);
        request.setGrossEarnings(BigDecimal.ZERO);
        return request;
    }
}
