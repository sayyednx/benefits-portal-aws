package com.nabeelsayyed.benefitsportal.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * A possible-fraud warning on a claim. A flag doesn't mean the claimant did anything wrong;
 * it means a person has to look before the claim can be approved. Clearing a flag needs a
 * note, and who cleared it is kept.
 */
@Entity
@Table(name = "claim_flags", indexes = @Index(name = "idx_flags_claim", columnList = "claimId"))
public class ClaimFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long claimId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 40)
    private FlagReason reason;

    @Column(nullable = false, updatable = false, length = 500)
    private String details;

    @Column(nullable = false, updatable = false)
    private LocalDateTime raisedAt;

    @Column(length = 100)
    private String resolvedBy;

    private LocalDateTime resolvedAt;

    @Column(length = 1000)
    private String resolutionNote;

    protected ClaimFlag() {
        // JPA
    }

    public ClaimFlag(Long claimId, FlagReason reason, String details, LocalDateTime raisedAt) {
        this.claimId = claimId;
        this.reason = reason;
        this.details = details;
        this.raisedAt = raisedAt;
    }

    public void resolve(String by, String note, LocalDateTime at) {
        this.resolvedBy = by;
        this.resolutionNote = note;
        this.resolvedAt = at;
    }

    public boolean isOpen() {
        return resolvedAt == null;
    }

    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public FlagReason getReason() { return reason; }
    public String getDetails() { return details; }
    public LocalDateTime getRaisedAt() { return raisedAt; }
    public String getResolvedBy() { return resolvedBy; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public String getResolutionNote() { return resolutionNote; }
}
