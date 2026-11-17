package com.nabeelsayyed.benefitsportal.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A claimant's weekly check-in. In real unemployment programs you don't just get approved
 * once and paid forever: every week you answer a few questions ("Were you able to work?
 * Did you look for work? Did you earn anything?") and that week is paid based on the answers.
 *
 * The unique constraint means a week can only be certified once per claim, even if two
 * requests arrive at the same moment.
 */
@Entity
@Table(name = "weekly_certifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_cert_claim_week", columnNames = {"claimId", "weekEnding"}))
public class WeeklyCertification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long claimId;

    /** The Saturday that ends the week being certified. */
    @Column(nullable = false, updatable = false)
    private LocalDate weekEnding;

    @Column(nullable = false, updatable = false)
    private boolean ableAndAvailable;

    @Column(nullable = false, updatable = false)
    private boolean refusedWork;

    @Column(nullable = false, updatable = false)
    private int jobContacts;

    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal grossEarnings;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 20)
    private CertificationOutcome outcome;

    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal amountPayable;

    @Column(updatable = false, length = 300)
    private String outcomeReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    protected WeeklyCertification() {
        // JPA
    }

    public WeeklyCertification(Long claimId, LocalDate weekEnding, boolean ableAndAvailable, boolean refusedWork,
                               int jobContacts, BigDecimal grossEarnings, CertificationOutcome outcome,
                               BigDecimal amountPayable, String outcomeReason, LocalDateTime submittedAt) {
        this.claimId = claimId;
        this.weekEnding = weekEnding;
        this.ableAndAvailable = ableAndAvailable;
        this.refusedWork = refusedWork;
        this.jobContacts = jobContacts;
        this.grossEarnings = grossEarnings;
        this.outcome = outcome;
        this.amountPayable = amountPayable;
        this.outcomeReason = outcomeReason;
        this.submittedAt = submittedAt;
    }

    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public LocalDate getWeekEnding() { return weekEnding; }
    public boolean isAbleAndAvailable() { return ableAndAvailable; }
    public boolean isRefusedWork() { return refusedWork; }
    public int getJobContacts() { return jobContacts; }
    public BigDecimal getGrossEarnings() { return grossEarnings; }
    public CertificationOutcome getOutcome() { return outcome; }
    public BigDecimal getAmountPayable() { return amountPayable; }
    public String getOutcomeReason() { return outcomeReason; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
}
