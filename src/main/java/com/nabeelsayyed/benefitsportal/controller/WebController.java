package com.nabeelsayyed.benefitsportal.controller;

import com.nabeelsayyed.benefitsportal.dto.ClaimDecisionRequest;
import com.nabeelsayyed.benefitsportal.dto.ClaimRequest;
import com.nabeelsayyed.benefitsportal.model.SeparationReason;
import com.nabeelsayyed.benefitsportal.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/** Server-rendered pages: the public claim form and the admin review queue. */
@Controller
public class WebController {

    private final ClaimService claimService;

    public WebController(ClaimService claimService) {
        this.claimService = claimService;
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
                : claimService.findByStatus(com.nabeelsayyed.benefitsportal.model.ClaimStatus.valueOf(status)));
        model.addAttribute("statuses", com.nabeelsayyed.benefitsportal.model.ClaimStatus.values());
        model.addAttribute("selectedStatus", status);
        return "admin";
    }

    @PostMapping("/admin/claims/{id}/decision")
    public String decide(@PathVariable Long id,
                          @RequestParam com.nabeelsayyed.benefitsportal.model.ClaimStatus status,
                          @RequestParam(required = false) String reviewerNotes) {
        ClaimDecisionRequest decision = new ClaimDecisionRequest();
        decision.setStatus(status);
        decision.setReviewerNotes(reviewerNotes);
        claimService.decide(id, decision);
        return "redirect:/admin/claims";
    }
}
