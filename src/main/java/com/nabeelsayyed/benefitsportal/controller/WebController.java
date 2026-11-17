package com.nabeelsayyed.benefitsportal.controller;

import com.nabeelsayyed.benefitsportal.dto.ClaimDecisionRequest;
import com.nabeelsayyed.benefitsportal.dto.ClaimRequest;
import com.nabeelsayyed.benefitsportal.exception.InvalidClaimActionException;
import com.nabeelsayyed.benefitsportal.model.ClaimStatus;
import com.nabeelsayyed.benefitsportal.model.SeparationReason;
import com.nabeelsayyed.benefitsportal.model.StaffRole;
import com.nabeelsayyed.benefitsportal.service.AuditService;
import com.nabeelsayyed.benefitsportal.service.CertificationService;
import com.nabeelsayyed.benefitsportal.service.ClaimService;
import com.nabeelsayyed.benefitsportal.service.DocumentService;
import com.nabeelsayyed.benefitsportal.service.FraudCheckService;
import com.nabeelsayyed.benefitsportal.service.StaffService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
    private final StaffService staffService;
    private final DocumentService documentService;
    private final FraudCheckService fraudCheckService;

    public WebController(ClaimService claimService, AuditService auditService,
                         CertificationService certificationService, StaffService staffService,
                         DocumentService documentService, FraudCheckService fraudCheckService) {
        this.claimService = claimService;
        this.auditService = auditService;
        this.certificationService = certificationService;
        this.staffService = staffService;
        this.documentService = documentService;
        this.fraudCheckService = fraudCheckService;
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
    public String adminDashboard(@RequestParam(required = false) String status,
                                 @RequestParam(required = false) boolean flagged,
                                 Model model) {
        var flaggedIds = fraudCheckService.claimIdsWithOpenFlags();
        var claims = status == null || status.isBlank()
                ? claimService.findAll()
                : claimService.findByStatus(ClaimStatus.valueOf(status));
        if (flagged) {
            claims = claims.stream().filter(c -> flaggedIds.contains(c.getId())).toList();
        }
        model.addAttribute("claims", claims);
        model.addAttribute("flaggedIds", flaggedIds);
        model.addAttribute("statuses", ClaimStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("flaggedOnly", flagged);
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
        model.addAttribute("documents", documentService.documentsFor(id));
        model.addAttribute("flags", fraudCheckService.flagsFor(id));
        model.addAttribute("hasOpenFlags", fraudCheckService.hasOpenFlags(id));
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
        } catch (InvalidClaimActionException | AccessDeniedException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/claims/" + id;
    }

    @PostMapping("/admin/claims/{id}/flags/{flagId}/resolve")
    public String resolveFlag(@PathVariable Long id, @PathVariable Long flagId,
                              @RequestParam(required = false) String note,
                              RedirectAttributes redirect) {
        try {
            fraudCheckService.resolve(id, flagId, note);
            redirect.addFlashAttribute("message", "Flag cleared.");
        } catch (InvalidClaimActionException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/claims/" + id;
    }

    /** Staff download of an uploaded document. Always a download, never shown inline in the browser. */
    @GetMapping("/admin/claims/{id}/documents/{documentId}")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long id, @PathVariable Long documentId) {
        var content = documentService.download(id, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.document().getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.document().getOriginalFilename()).build().toString())
                .body(content.bytes());
    }

    // --- Staff accounts (supervisors only; see SecurityConfig) ---

    @GetMapping("/admin/staff")
    public String staffPage(Model model) {
        model.addAttribute("staff", staffService.findAll());
        model.addAttribute("roles", StaffRole.values());
        return "staff";
    }

    @PostMapping("/admin/staff")
    public String createStaff(@RequestParam String username,
                              @RequestParam String fullName,
                              @RequestParam(required = false) StaffRole role,
                              @RequestParam String password,
                              RedirectAttributes redirect) {
        try {
            var created = staffService.create(username, fullName, role, password);
            redirect.addFlashAttribute("message", "Created " + created.getUsername() + " as " + created.getRole() + ".");
        } catch (InvalidClaimActionException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/staff";
    }

    @PostMapping("/admin/staff/{id}/active")
    public String setStaffActive(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes redirect) {
        try {
            var staff = staffService.setActive(id, active);
            redirect.addFlashAttribute("message",
                    staff.getUsername() + (active ? " can log in again." : " can no longer log in."));
        } catch (InvalidClaimActionException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/staff";
    }
}
