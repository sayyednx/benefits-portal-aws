package com.nabeelsayyed.benefitsportal.notification;

import com.nabeelsayyed.benefitsportal.model.Claim;
import com.nabeelsayyed.benefitsportal.model.ClaimStatus;
import com.nabeelsayyed.benefitsportal.service.ClaimService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Turns claim events into emails to the claimant.
 *
 * Two choices keep email from ever breaking the main work:
 * - It runs only AFTER the database change is committed. If filing a claim fails and is
 *   rolled back, no "we received your claim" email goes out by mistake.
 * - It runs in the background (@Async) and catches its own errors. If the mail server is
 *   down, the claim is still saved; the problem is written to the log instead.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    private final ClaimService claimService;
    private final EmailSender emailSender;
    private final ITemplateEngine templateEngine;

    @Value("${notifications.enabled:true}")
    private boolean enabled;

    /** The web address of the portal, used for links in emails. */
    @Value("${notifications.base-url:http://localhost:8080}")
    private String baseUrl;

    public NotificationService(ClaimService claimService, EmailSender emailSender, ITemplateEngine templateEngine) {
        this.claimService = claimService;
        this.emailSender = emailSender;
        this.templateEngine = templateEngine;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClaimEvent(ClaimNotificationEvent event) {
        if (!enabled) {
            return;
        }
        try {
            Claim claim = claimService.findById(event.claimId());
            Notice notice = build(claim, event);
            if (notice == null) {
                return;
            }
            emailSender.send(claim.getClaimantEmail(), notice.subject(), renderHtml(notice), renderText(notice));
            log.info("Sent {} email for claim {}", event.kind(), claim.getId());
        } catch (RuntimeException e) {
            log.warn("Could not send {} email for claim {}: {}", event.kind(), event.claimId(), e.getMessage());
        }
    }

    /** Works out what each email says. Returns null when no email is needed. */
    Notice build(Claim claim, ClaimNotificationEvent event) {
        String statusUrl = baseUrl + "/status";
        String number = claim.getConfirmationNumber();
        String firstName = claim.getClaimantName().trim().split("\\s+")[0];

        return switch (event.kind()) {
            case CLAIM_FILED -> {
                var highlights = new LinkedHashMap<String, String>();
                highlights.put("Confirmation number", number);
                highlights.put("Employer", claim.getEmployerName());
                highlights.put("Estimated weekly benefit", claim.isEligible()
                        ? "$" + claim.getWeeklyBenefitAmount() : "Likely not eligible");
                yield new Notice("We received your unemployment claim (" + number + ")",
                        "We received your claim",
                        "Hi " + firstName + ", thank you for filing. Keep this email: you'll need your confirmation "
                                + "number and this email address to check on your claim.",
                        highlights,
                        List.of("What happens next: a caseworker will review your claim, usually within a few days. "
                                        + "They may contact your employer to confirm the details.",
                                "You can speed things up by uploading a layoff letter or recent pay stub on the "
                                        + "Check my claim page."),
                        "Check my claim", statusUrl);
            }
            case DECISION_MADE -> {
                if (claim.getStatus() == ClaimStatus.APPROVED) {
                    var highlights = new LinkedHashMap<String, String>();
                    highlights.put("Confirmation number", number);
                    highlights.put("Weekly benefit", "$" + claim.getWeeklyBenefitAmount());
                    yield new Notice("Your unemployment claim is approved (" + number + ")",
                            "Your claim is approved",
                            "Hi " + firstName + ", good news: your unemployment claim has been approved.",
                            highlights,
                            List.of("To get paid, check in every week. After each week ends (Saturday), answer a few "
                                            + "questions about your job search and any earnings on the Check my claim page.",
                                    "Keep a list of the employers you contact each week."),
                            "Check in for this week", statusUrl);
                }
                if (claim.getStatus() == ClaimStatus.DENIED) {
                    var highlights = new LinkedHashMap<String, String>();
                    highlights.put("Confirmation number", number);
                    if (claim.getReviewerNotes() != null) {
                        highlights.put("Reason", claim.getReviewerNotes());
                    }
                    var deadline = claimService.appealDeadline(claim);
                    boolean canAppeal = claimService.canAppeal(claim);
                    yield new Notice("A decision on your unemployment claim (" + number + ")",
                            "Your claim was denied",
                            "Hi " + firstName + ", we reviewed your claim and were not able to approve it.",
                            highlights,
                            canAppeal && deadline != null
                                    ? List.of("If you think this decision is wrong, you can appeal. A different reviewer "
                                    + "will look at your claim again. You have until " + deadline.format(LONG_DATE)
                                    + " to appeal.")
                                    : List.of("This decision is final."),
                            canAppeal ? "Appeal this decision" : "View my claim", statusUrl);
                }
                yield null; // other moves (like "under review") don't need an email
            }
            case APPEAL_RECEIVED -> {
                var highlights = new LinkedHashMap<String, String>();
                highlights.put("Confirmation number", number);
                yield new Notice("We received your appeal (" + number + ")",
                        "We received your appeal",
                        "Hi " + firstName + ", we received your appeal. A supervisor who was not part of the first "
                                + "decision will review your claim again.",
                        highlights,
                        List.of("You can upload more proof, like a layoff letter, on the Check my claim page while "
                                + "your appeal is in review. We'll email you when there's a decision."),
                        "View my claim", statusUrl);
            }
            case PAYMENT_SENT -> {
                var highlights = new LinkedHashMap<String, String>();
                highlights.put("Payment", event.extra());
                highlights.put("Confirmation number", number);
                yield new Notice("Your weekly benefit payment was sent",
                        "Your payment was sent",
                        "Hi " + firstName + ", we sent your unemployment payment.",
                        highlights,
                        List.of("Remember to check in again after next Saturday to get paid for next week."),
                        "View my payments", statusUrl);
            }
        };
    }

    String renderHtml(Notice notice) {
        Context context = new Context();
        context.setVariable("notice", notice);
        return templateEngine.process("email/notice", context);
    }

    /** A plain-text copy of the email, for mail apps that don't show HTML. */
    String renderText(Notice notice) {
        StringBuilder text = new StringBuilder();
        text.append(notice.heading()).append("\n\n").append(notice.intro()).append("\n\n");
        notice.highlights().forEach((label, value) -> text.append(label).append(": ").append(value).append("\n"));
        text.append("\n");
        notice.details().forEach(line -> text.append(line).append("\n\n"));
        text.append(notice.buttonText()).append(": ").append(notice.buttonUrl()).append("\n\n");
        text.append("--\nBenefits Portal. A portfolio demo by Nabeel Sayyed, not a real government service.\n");
        return text.toString();
    }
}
