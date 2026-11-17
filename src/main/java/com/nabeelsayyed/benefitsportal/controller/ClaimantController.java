package com.nabeelsayyed.benefitsportal.controller;

import com.nabeelsayyed.benefitsportal.dto.CertificationRequest;
import com.nabeelsayyed.benefitsportal.exception.InvalidClaimActionException;
import com.nabeelsayyed.benefitsportal.model.Claim;
import com.nabeelsayyed.benefitsportal.model.DocumentType;
import com.nabeelsayyed.benefitsportal.service.AuditService;
import com.nabeelsayyed.benefitsportal.service.CertificationService;
import com.nabeelsayyed.benefitsportal.service.ClaimService;
import com.nabeelsayyed.benefitsportal.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
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
    private final CertificationService certificationService;
    private final DocumentService documentService;

    public ClaimantController(ClaimService claimService, AuditService auditService,
                              CertificationService certificationService, DocumentService documentService) {
        this.claimService = claimService;
        this.auditService = auditService;
        this.certificationService = certificationService;
        this.documentService = documentService;
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
        // The page shows only what happened and when. Staff usernames and fraud flags stay on the staff side.
        model.addAttribute("history", auditService.historyFor(claimId).stream()
                .filter(e -> e.getType().isVisibleToClaimant())
                .toList());
        model.addAttribute("canAppeal", claimService.canAppeal(claim));
        model.addAttribute("appealDeadline", claimService.appealDeadline(claim));

        List<LocalDate> weeksDue = certificationService.weeksDue(claim);
        model.addAttribute("weeksDue", weeksDue);
        model.addAttribute("certifications", certificationService.certificationsFor(claimId));
        model.addAttribute("totalPaid", certificationService.totalPaid(claimId));
        model.addAttribute("remainingBalance", certificationService.remainingBalance(claim));
        model.addAttribute("documents", documentService.documentsFor(claimId));
        model.addAttribute("documentTypes", DocumentType.values());
        if (!model.containsAttribute("certification")) {
            CertificationRequest form = new CertificationRequest();
            form.setWeekEnding(weeksDue.isEmpty() ? null : weeksDue.get(0));
            model.addAttribute("certification", form);
        }
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

    @PostMapping("/status/certify")
    public String certify(@Valid @ModelAttribute("certification") CertificationRequest certification,
                          BindingResult bindingResult,
                          HttpSession session,
                          RedirectAttributes redirect) {
        Long claimId = (Long) session.getAttribute(VERIFIED_CLAIM);
        if (claimId == null) {
            return "redirect:/status";
        }
        if (bindingResult.hasErrors()) {
            redirect.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            redirect.addFlashAttribute("certification", certification);
            return "redirect:/status/claim";
        }
        try {
            var result = certificationService.certify(claimId, certification);
            redirect.addFlashAttribute("message", "Week ending " + result.getWeekEnding() + ": " + result.getOutcomeReason());
        } catch (InvalidClaimActionException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/status/claim";
    }

    @PostMapping("/status/documents")
    public String uploadDocument(@RequestParam(required = false) DocumentType type,
                                 @RequestParam(required = false) MultipartFile file,
                                 HttpSession session,
                                 RedirectAttributes redirect) {
        Long claimId = (Long) session.getAttribute(VERIFIED_CLAIM);
        if (claimId == null) {
            return "redirect:/status";
        }
        try {
            var saved = documentService.upload(claimId, type, file);
            redirect.addFlashAttribute("message", "Uploaded " + saved.getOriginalFilename() + ".");
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
