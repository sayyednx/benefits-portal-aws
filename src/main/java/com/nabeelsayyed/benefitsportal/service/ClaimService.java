package com.nabeelsayyed.benefitsportal.service;

import com.nabeelsayyed.benefitsportal.dto.ClaimDecisionRequest;
import com.nabeelsayyed.benefitsportal.dto.ClaimRequest;
import com.nabeelsayyed.benefitsportal.exception.ClaimNotFoundException;
import com.nabeelsayyed.benefitsportal.model.Claim;
import com.nabeelsayyed.benefitsportal.model.ClaimEventType;
import com.nabeelsayyed.benefitsportal.model.ClaimStatus;
import com.nabeelsayyed.benefitsportal.model.SeparationReason;
import com.nabeelsayyed.benefitsportal.repository.ClaimRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final AuditService auditService;
    private final CurrentActor currentActor;
    private final Clock clock;

    /**
     * The percentage of average weekly wage paid out as a benefit. Real state systems
     * usually land somewhere between 40-60%; this is configurable via
     * benefits.wage-replacement-rate in application.yml.
     */
    @Value("${benefits.wage-replacement-rate:0.5}")
    private BigDecimal wageReplacementRate;

    /** The maximum weekly benefit the program will pay, regardless of prior wage. */
    @Value("${benefits.max-weekly-benefit:500.00}")
    private BigDecimal maxWeeklyBenefit;

    /** The minimum weekly benefit paid to anyone found monetarily eligible. */
    @Value("${benefits.min-weekly-benefit:50.00}")
    private BigDecimal minWeeklyBenefit;

    /**
     * Separation reasons that disqualify a claimant outright, mirroring the real-world
     * rule that voluntarily quitting without cause or being fired for misconduct
     * generally isn't compensable.
     */
    private static final Set<SeparationReason> DISQUALIFYING_REASONS = Set.of(
            SeparationReason.FIRED_MISCONDUCT,
            SeparationReason.QUIT_NO_CAUSE
    );

    public ClaimService(ClaimRepository claimRepository, AuditService auditService,
                        CurrentActor currentActor, Clock clock) {
        this.claimRepository = claimRepository;
        this.auditService = auditService;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    @Transactional
    public Claim fileClaim(ClaimRequest request) {
        Claim claim = new Claim(
                request.getClaimantName(),
                request.getClaimantEmail(),
                request.getEmployerName(),
                request.getLastDayWorked(),
                request.getSeparationReason(),
                request.getAverageWeeklyWage(),
                LocalDateTime.now(clock)
        );

        boolean eligible = isMonetarilyEligible(claim.getSeparationReason());
        claim.setEligible(eligible);
        claim.setWeeklyBenefitAmount(eligible ? calculateWeeklyBenefit(claim.getAverageWeeklyWage()) : BigDecimal.ZERO);
        claim.setStatus(ClaimStatus.FILED);

        Claim saved = claimRepository.save(claim);
        auditService.record(saved.getId(), ClaimEventType.FILED, null, ClaimStatus.FILED,
                eligible ? "Weekly benefit calculated: $" + saved.getWeeklyBenefitAmount()
                         : "Not monetarily eligible: " + saved.getSeparationReason());
        return saved;
    }

    /**
     * Whether the claimant's separation reason qualifies them for benefits at all.
     * This runs before the dollar-amount calculation — an ineligible claim never
     * gets a benefit amount above zero, no matter what they earned.
     */
    public boolean isMonetarilyEligible(SeparationReason reason) {
        return !DISQUALIFYING_REASONS.contains(reason);
    }

    /**
     * weeklyBenefit = averageWeeklyWage * replacementRate, clamped to
     * [minWeeklyBenefit, maxWeeklyBenefit] and rounded to the nearest cent.
     */
    public BigDecimal calculateWeeklyBenefit(BigDecimal averageWeeklyWage) {
        BigDecimal raw = averageWeeklyWage.multiply(wageReplacementRate)
                .setScale(2, RoundingMode.HALF_UP);

        if (raw.compareTo(maxWeeklyBenefit) > 0) {
            return maxWeeklyBenefit;
        }
        if (raw.compareTo(minWeeklyBenefit) < 0) {
            return minWeeklyBenefit;
        }
        return raw;
    }

    @Transactional(readOnly = true)
    public List<Claim> findAll() {
        return claimRepository.findAllByOrderByFiledAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Claim> findByStatus(ClaimStatus status) {
        return claimRepository.findAllByStatusOrderByFiledAtDesc(status);
    }

    @Transactional(readOnly = true)
    public Claim findById(Long id) {
        return claimRepository.findById(id).orElseThrow(() -> new ClaimNotFoundException(id));
    }

    @Transactional
    public Claim decide(Long id, ClaimDecisionRequest decision) {
        Claim claim = findById(id);
        ClaimStatus from = claim.getStatus();
        claim.setStatus(decision.getStatus());
        claim.setReviewerNotes(decision.getReviewerNotes());
        claim.setReviewedAt(LocalDateTime.now(clock));
        claim.setReviewedBy(currentActor.name());
        Claim saved = claimRepository.save(claim);
        auditService.record(saved.getId(), ClaimEventType.STATUS_CHANGED, from, decision.getStatus(),
                decision.getReviewerNotes());
        return saved;
    }
}
