package com.nabeelsayyed.benefitsportal.controller;

import com.nabeelsayyed.benefitsportal.exception.InvalidClaimActionException;
import com.nabeelsayyed.benefitsportal.model.Claim;
import com.nabeelsayyed.benefitsportal.service.AuditService;
import com.nabeelsayyed.benefitsportal.service.ClaimService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * The claimant's side after filing: "check my claim", and appealing a denial.
 *
 * Claimants don't have passwords in this app. Instead they prove who they are with their
 * confirmation number plus their email. Once that matches, the claim id is remembered in
 * their browser session, so the next pages know which claim is theirs without asking again.
 */
@Controller
public class ClaimantController {

    /** The session key that holds the id of the claim this visitor has proved is theirs. */
    static final String VERIFIED_CLAIM = "verifiedClaimId";

    private final ClaimService claimService;
    private final AuditService auditService;

    public ClaimantController(ClaimService claimService, AuditService auditService) {
        this.claimService = claimService;
        this.auditService = auditService;
    }

    @GetMapping("/status")
    public String lookupForm() {
        return "status-lookup";
    }

    @PostMapping("/status")
    public String lookup(@RequestParam String confirmationNumber,
                         @RequestParam String email,
                         HttpServletRequest request,
                         Model model) {
        Optional<Claim> claim = claimService.lookup(confirmationNumber, email);
        if (claim.isEmpty()) {
            // Same message whichever part was wrong, so this page can't confirm whose claims exist.
            model.addAttribute("error", "We couldn't find a claim with that confirmation number and email.");
            model.addAttribute("confirmationNumber", confirmationNumber);
            return "status-lookup";
        }
        // New session id after "logging in" this way, so an old id someone else saw is useless.
        request.getSession(true);
        request.changeSessionId();
        request.getSession().setAttribute(VERIFIED_CLAIM, claim.get().getId());
        return "redirect:/status/claim";
    }

    @GetMapping("/status/claim")
    public String claimStatus(HttpSession session, Model model) {
        Long claimId = (Long) session.getAttribute(VERIFIED_CLAIM);
        if (claimId == null) {
            return "redirect:/status";
        }
        Claim claim = claimService.findById(claimId);
        model.addAttribute("claim", claim);
        // The page shows only what happened and when. Staff usernames stay on the staff side.
        model.addAttribute("history", auditService.historyFor(claimId));
        model.addAttribute("canAppeal", claimService.canAppeal(claim));
        model.addAttribute("appealDeadline", claimService.appealDeadline(claim));
        return "claim-status";
    }

    @PostMapping("/status/appeal")
    public String appeal(@RequestParam(required = false) String reason,
                         HttpSession session,
                         RedirectAttributes redirect) {
        Long claimId = (Long) session.getAttribute(VERIFIED_CLAIM);
        if (claimId == null) {
            return "redirect:/status";
        }
        try {
            claimService.fileAppeal(claimId, reason);
            redirect.addFlashAttribute("message", "Your appeal was filed. A different reviewer will look at your claim.");
        } catch (InvalidClaimActionException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/status/claim";
    }

    @PostMapping("/status/signout")
    public String signOut(HttpSession session) {
        session.invalidate();
        return "redirect:/status";
    }
}
