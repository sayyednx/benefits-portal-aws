package com.nabeelsayyed.benefitsportal.service;

import com.nabeelsayyed.benefitsportal.dto.ClaimDecisionRequest;
import com.nabeelsayyed.benefitsportal.dto.ClaimRequest;
import com.nabeelsayyed.benefitsportal.exception.ClaimNotFoundException;
import com.nabeelsayyed.benefitsportal.exception.InvalidClaimActionException;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final AuditService auditService;
    private final CurrentActor currentActor;
    private final ConfirmationNumberGenerator confirmationNumbers;
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
     * How many days a claimant has to appeal a denial, counted from the day of the decision.
     * Most states give somewhere between 10 and 30 days.
     */
    @Value("${benefits.appeal-window-days:30}")
    private int appealWindowDays;

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
                        CurrentActor currentActor, ConfirmationNumberGenerator confirmationNumbers,
                        Clock clock) {
        this.claimRepository = claimRepository;
        this.auditService = auditService;
        this.currentActor = currentActor;
        this.confirmationNumbers = confirmationNumbers;
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
        claim.setConfirmationNumber(newConfirmationNumber());

        Claim saved = claimRepository.save(claim);
        auditService.record(saved.getId(), ClaimEventType.FILED, null, ClaimStatus.FILED,
                eligible ? "Weekly benefit calculated: $" + saved.getWeeklyBenefitAmount()
                         : "Not monetarily eligible: " + saved.getSeparationReason());
        return saved;
    }

    private String newConfirmationNumber() {
        // About a trillion possible numbers, so a clash is very unlikely, but check anyway.
        String number;
        do {
            number = confirmationNumbers.next();
        } while (claimRepository.existsByConfirmationNumber(number));
        return number;
    }

    /**
     * Finds a claim for the "check my claim" page. Both the confirmation number and the
     * email must match. When either is wrong the result is simply empty, so the page can't
     * be used to find out which emails have claims.
     */
    @Transactional(readOnly = true)
    public Optional<Claim> lookup(String confirmationNumber, String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return claimRepository.findByConfirmationNumber(ConfirmationNumberGenerator.normalize(confirmationNumber))
                .filter(claim -> claim.getClaimantEmail().equalsIgnoreCase(email.trim()));
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

    /**
     * A staff decision on a claim. Enforces the program's rules before anything is saved:
     *
     * - the move must be allowed from the claim's current status (see ClaimStatus);
     * - a denial must say why, because claimants have a right to know the reason;
     * - approving a claim that was found ineligible means the reviewer is overturning that
     *   finding (for example, after an appeal), so the benefit is recalculated.
     */
    @Transactional
    public Claim decide(Long id, ClaimDecisionRequest decision) {
        Claim claim = findById(id);
        ClaimStatus from = claim.getStatus();
        ClaimStatus to = decision.getStatus();
        String notes = decision.getReviewerNotes() == null ? null : decision.getReviewerNotes().trim();

        if (!from.staffCanMoveTo().contains(to)) {
            throw new InvalidClaimActionException(
                    "A claim that is " + from + " can't be moved to " + to + ".");
        }
        if (to == ClaimStatus.DENIED && (notes == null || notes.isEmpty())) {
            throw new InvalidClaimActionException("Add a note explaining why the claim is denied.");
        }

        String details = notes;
        if (to == ClaimStatus.APPROVED && !claim.isEligible()) {
            claim.setEligible(true);
            claim.setWeeklyBenefitAmount(calculateWeeklyBenefit(claim.getAverageWeeklyWage()));
            details = "Ineligibility finding overturned; weekly benefit set to $"
                    + claim.getWeeklyBenefitAmount() + (notes == null || notes.isEmpty() ? "" : ". " + notes);
        }

        claim.setStatus(to);
        claim.setReviewerNotes(notes);
        claim.setReviewedAt(LocalDateTime.now(clock));
        claim.setReviewedBy(currentActor.name());
        Claim saved = claimRepository.save(claim);
        auditService.record(saved.getId(), ClaimEventType.STATUS_CHANGED, from, to, details);
        return saved;
    }

    /** The last day the claimant may appeal, or null if the claim isn't denied. */
    public LocalDate appealDeadline(Claim claim) {
        if (claim.getStatus() != ClaimStatus.DENIED || claim.getReviewedAt() == null) {
            return null;
        }
        return claim.getReviewedAt().toLocalDate().plusDays(appealWindowDays);
    }

    /** Whether the claimant can still appeal: denied, never appealed, and inside the window. */
    public boolean canAppeal(Claim claim) {
        LocalDate deadline = appealDeadline(claim);
        return deadline != null
                && claim.getAppealedAt() == null
                && !LocalDate.now(clock).isAfter(deadline);
    }

    /**
     * The claimant asks for a second look at a denial. Checking that the person really is
     * the claimant is the caller's job (see the "check my claim" page); this method only
     * enforces the program's appeal rules.
     */
    @Transactional
    public Claim fileAppeal(Long id, String reason) {
        Claim claim = findById(id);
        if (claim.getStatus() != ClaimStatus.DENIED) {
            throw new InvalidClaimActionException("Only a denied claim can be appealed.");
        }
        if (claim.getAppealedAt() != null) {
            throw new InvalidClaimActionException("This claim has already been appealed once.");
        }
        if (!canAppeal(claim)) {
            throw new InvalidClaimActionException(
                    "The deadline to appeal was " + appealDeadline(claim) + ".");
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidClaimActionException("Tell us why you think the decision was wrong.");
        }

        claim.setStatus(ClaimStatus.APPEALED);
        claim.setAppealedAt(LocalDateTime.now(clock));
        claim.setAppealReason(reason.trim());
        Claim saved = claimRepository.save(claim);
        auditService.record(saved.getId(), ClaimEventType.APPEAL_FILED,
                ClaimStatus.DENIED, ClaimStatus.APPEALED, reason.trim());
        return saved;
    }
}
