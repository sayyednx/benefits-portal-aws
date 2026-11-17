package com.nabeelsayyed.benefitsportal.controller;

import com.nabeelsayyed.benefitsportal.dto.ClaimDecisionRequest;
import com.nabeelsayyed.benefitsportal.dto.ClaimRequest;
import com.nabeelsayyed.benefitsportal.model.Claim;
import com.nabeelsayyed.benefitsportal.model.ClaimDocument;
import com.nabeelsayyed.benefitsportal.model.ClaimEvent;
import com.nabeelsayyed.benefitsportal.model.ClaimStatus;
import com.nabeelsayyed.benefitsportal.model.Payment;
import com.nabeelsayyed.benefitsportal.model.WeeklyCertification;
import com.nabeelsayyed.benefitsportal.service.AuditService;
import com.nabeelsayyed.benefitsportal.service.CertificationService;
import com.nabeelsayyed.benefitsportal.service.ClaimService;
import com.nabeelsayyed.benefitsportal.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * JSON API behind the web pages. Kept alongside the Thymeleaf controller (rather than
 * building a separate front end) to keep this project's scope realistic — but it's a
 * real REST surface you could point a separate client at.
 */
@RestController
@RequestMapping("/api/claims")
public class ClaimApiController {

    private final ClaimService claimService;
    private final AuditService auditService;
    private final CertificationService certificationService;
    private final DocumentService documentService;

    public ClaimApiController(ClaimService claimService, AuditService auditService,
                              CertificationService certificationService, DocumentService documentService) {
        this.claimService = claimService;
        this.auditService = auditService;
        this.certificationService = certificationService;
        this.documentService = documentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Claim fileClaim(@Valid @RequestBody ClaimRequest request) {
        return claimService.fileClaim(request);
    }

    @GetMapping
    public List<Claim> listClaims(@RequestParam(required = false) ClaimStatus status) {
        return status == null ? claimService.findAll() : claimService.findByStatus(status);
    }

    @GetMapping("/{id}")
    public Claim getClaim(@PathVariable Long id) {
        return claimService.findById(id);
    }

    /** The claim's full audit history, oldest first. */
    @GetMapping("/{id}/history")
    public List<ClaimEvent> history(@PathVariable Long id) {
        claimService.findById(id); // 404 if the claim doesn't exist
        return auditService.historyFor(id);
    }

    @GetMapping("/{id}/certifications")
    public List<WeeklyCertification> certifications(@PathVariable Long id) {
        claimService.findById(id);
        return certificationService.certificationsFor(id);
    }

    @GetMapping("/{id}/payments")
    public List<Payment> payments(@PathVariable Long id) {
        claimService.findById(id);
        return certificationService.paymentsFor(id);
    }

    /** Information about the claim's uploaded documents (not the files themselves). */
    @GetMapping("/{id}/documents")
    public List<ClaimDocument> documents(@PathVariable Long id) {
        claimService.findById(id);
        return documentService.documentsFor(id);
    }

    @PatchMapping("/{id}/status")
    public Claim decide(@PathVariable Long id, @Valid @RequestBody ClaimDecisionRequest decision) {
        return claimService.decide(id, decision);
    }
}
