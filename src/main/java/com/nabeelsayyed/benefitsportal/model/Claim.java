package com.nabeelsayyed.benefitsportal.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "claims")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String claimantName;

    @Column(nullable = false)
    private String claimantEmail;

    @Column(nullable = false)
    private String employerName;

    @Column(nullable = false)
    private LocalDate lastDayWorked;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeparationReason separationReason;

    /** What the claimant reports earning per week while employed. Drives the benefit calc below. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal averageWeeklyWage;

    /** Computed at filing time — see ClaimService.calculateWeeklyBenefit(). */
    @Column(precision = 10, scale = 2)
    private BigDecimal weeklyBenefitAmount;

    /** Computed at filing time — see ClaimService.isMonetarilyEligible(). */
    @Column(nullable = false)
    private boolean eligible;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status = ClaimStatus.FILED;

    @Column(nullable = false)
    private LocalDateTime filedAt;

    private LocalDateTime reviewedAt;

    @Column(length = 1000)
    private String reviewerNotes;

    protected Claim() {
        // JPA
    }

    public Claim(String claimantName, String claimantEmail, String employerName,
                 LocalDate lastDayWorked, SeparationReason separationReason,
                 BigDecimal averageWeeklyWage) {
        this.claimantName = claimantName;
        this.claimantEmail = claimantEmail;
        this.employerName = employerName;
        this.lastDayWorked = lastDayWorked;
        this.separationReason = separationReason;
        this.averageWeeklyWage = averageWeeklyWage;
        this.filedAt = LocalDateTime.now();
    }

    // --- getters / setters ---

    public Long getId() {
        return id;
    }

    public String getClaimantName() {
        return claimantName;
    }

    public String getClaimantEmail() {
        return claimantEmail;
    }

    public String getEmployerName() {
        return employerName;
    }

    public LocalDate getLastDayWorked() {
        return lastDayWorked;
    }

    public SeparationReason getSeparationReason() {
        return separationReason;
    }

    public BigDecimal getAverageWeeklyWage() {
        return averageWeeklyWage;
    }

    public BigDecimal getWeeklyBenefitAmount() {
        return weeklyBenefitAmount;
    }

    public void setWeeklyBenefitAmount(BigDecimal weeklyBenefitAmount) {
        this.weeklyBenefitAmount = weeklyBenefitAmount;
    }

    public boolean isEligible() {
        return eligible;
    }

    public void setEligible(boolean eligible) {
        this.eligible = eligible;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public void setStatus(ClaimStatus status) {
        this.status = status;
    }

    public LocalDateTime getFiledAt() {
        return filedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewerNotes() {
        return reviewerNotes;
    }

    public void setReviewerNotes(String reviewerNotes) {
        this.reviewerNotes = reviewerNotes;
    }
}
