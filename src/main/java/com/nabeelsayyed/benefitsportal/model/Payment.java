package com.nabeelsayyed.benefitsportal.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One benefit payment: a line in the claim's payment ledger. Like the audit history, it is
 * append-only. A real system would also track whether the bank transfer went through; here
 * a payment is recorded as issued the moment the week is found payable.
 */
@Entity
@Table(name = "payments", indexes = @Index(name = "idx_payments_claim", columnList = "claimId"))
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long claimId;

    @Column(nullable = false, updatable = false, unique = true)
    private Long certificationId;

    @Column(nullable = false, updatable = false)
    private LocalDate weekEnding;

    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    protected Payment() {
        // JPA
    }

    public Payment(Long claimId, Long certificationId, LocalDate weekEnding, BigDecimal amount, LocalDateTime issuedAt) {
        this.claimId = claimId;
        this.certificationId = certificationId;
        this.weekEnding = weekEnding;
        this.amount = amount;
        this.issuedAt = issuedAt;
    }

    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public Long getCertificationId() { return certificationId; }
    public LocalDate getWeekEnding() { return weekEnding; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
}
