package com.nabeelsayyed.benefitsportal.controller;

import com.nabeelsayyed.benefitsportal.dto.ClaimDecisionRequest;
import com.nabeelsayyed.benefitsportal.dto.ClaimRequest;
import com.nabeelsayyed.benefitsportal.exception.InvalidClaimActionException;
import com.nabeelsayyed.benefitsportal.model.ClaimStatus;
import com.nabeelsayyed.benefitsportal.model.SeparationReason;
import com.nabeelsayyed.benefitsportal.service.AuditService;
import com.nabeelsayyed.benefitsportal.service.CertificationService;
import com.nabeelsayyed.benefitsportal.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Server-rendered pages: the public claim form and the admin review queue. */
@Controller
public class WebController {

    private final ClaimService claimService;
    private final AuditService auditService;
    private final CertificationService certificationService;

    public WebController(ClaimService claimService, AuditService auditService,
                         CertificationService certificationService) {
        this.claimService = claimService;
        this.auditService = auditService;
        this.certificationService = certificationService;
    }

    @GetMapping("/")
    public String claimForm(Model model) {
        if (!model.containsAttribute("claimRequest")) {
            model.addAttribute("claimRequest", new ClaimRequest());
        }
        model.addAttribute("separationReasons", SeparationReason.values());
        return "index";
    }

    @PostMapping("/claims")
    public String submitClaim(@Valid @ModelAttribute("claimRequest") ClaimRequest claimRequest,
                               BindingResult bindingResult,
                               Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("separationReasons", SeparationReason.values());
            return "index";
        }
        var claim = claimService.fileClaim(claimRequest);
        model.addAttribute("claim", claim);
        return "confirmation";
    }

    @GetMapping("/admin/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/admin/claims")
    public String adminDashboard(@RequestParam(required = false) String status, Model model) {
        model.addAttribute("claims", status == null || status.isBlank()
                ? claimService.findAll()
                : claimService.findByStatus(ClaimStatus.valueOf(status)));
        model.addAttribute("statuses", ClaimStatus.values());
        model.addAttribute("selectedStatus", status);
        return "admin";
    }

    /** One claim in full: details, weekly certifications and payments, and audit history. */
    @GetMapping("/admin/claims/{id}")
    public String claimDetail(@PathVariable Long id, Model model) {
        var claim = claimService.findById(id);
        model.addAttribute("claim", claim);
        model.addAttribute("history", auditService.historyFor(id));
        model.addAttribute("certifications", certificationService.certificationsFor(id));
        model.addAttribute("totalPaid", certificationService.totalPaid(id));
        model.addAttribute("maximumBenefit", certificationService.maximumBenefit(claim));
        model.addAttribute("remainingBalance", certificationService.remainingBalance(claim));
        return "claim-detail";
    }

    @PostMapping("/admin/claims/{id}/decision")
    public String decide(@PathVariable Long id,
                          @RequestParam ClaimStatus status,
                          @RequestParam(required = false) String reviewerNotes,
                          RedirectAttributes redirect) {
        ClaimDecisionRequest decision = new ClaimDecisionRequest();
        decision.setStatus(status);
        decision.setReviewerNotes(reviewerNotes);
        try {
            claimService.decide(id, decision);
            redirect.addFlashAttribute("message", "Claim #" + id + " is now " + status + ".");
        } catch (InvalidClaimActionException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/claims/" + id;
    }
}
