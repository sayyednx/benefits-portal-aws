package com.nabeelsayyed.benefitsportal.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * One line in a claim's audit history: who did what, and when.
 *
 * Government benefit programs are required to keep a record like this for every case, so an
 * auditor can rebuild exactly how a decision was reached. Two design choices follow from that:
 *
 * - Events are append-only. Every column is {@code updatable = false} and there are no
 *   setters, so once a row is written the app has no way to change it.
 * - Events point at the claim by its id rather than through a JPA relationship, so the
 *   history stays a plain, independent record and is easy to return from the API.
 */
@Entity
@Table(name = "claim_events", indexes = @Index(name = "idx_claim_events_claim", columnList = "claimId"))
public class ClaimEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long claimId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 40)
    private ClaimEventType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(updatable = false, length = 32)
    private ClaimStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(updatable = false, length = 32)
    private ClaimStatus toStatus;

    /** The staff username who did this, or "claimant" / "system" for non-staff actions. */
    @Column(nullable = false, updatable = false, length = 100)
    private String actor;

    @Column(updatable = false, length = 1000)
    private String details;

    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    protected ClaimEvent() {
        // JPA
    }

    public ClaimEvent(Long claimId, ClaimEventType type, ClaimStatus fromStatus, ClaimStatus toStatus,
                      String actor, String details, LocalDateTime occurredAt) {
        this.claimId = claimId;
        this.type = type;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.details = details;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public ClaimEventType getType() { return type; }
    public ClaimStatus getFromStatus() { return fromStatus; }
    public ClaimStatus getToStatus() { return toStatus; }
    public String getActor() { return actor; }
    public String getDetails() { return details; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
