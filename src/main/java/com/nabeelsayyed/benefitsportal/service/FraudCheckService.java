package com.nabeelsayyed.benefitsportal.service;

import com.nabeelsayyed.benefitsportal.exception.InvalidClaimActionException;
import com.nabeelsayyed.benefitsportal.model.*;
import com.nabeelsayyed.benefitsportal.repository.ClaimFlagRepository;
import com.nabeelsayyed.benefitsportal.repository.ClaimRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Simple automatic checks that run on every new claim, looking for common signs of fraud
 * or mistakes. Anything suspicious gets a flag, and a flagged claim can't be approved until
 * a staff member looks at it and clears the flag with a note.
 *
 * These are basic rules, not a fraud-detection model. Real programs also cross-check
 * outside data (employer wage records, death records, prison records, other states).
 */
@Service
public class FraudCheckService {

    /** Claims that are still active, so a second one at the same time is suspicious. */
    static final Set<ClaimStatus> OPEN_STATUSES = EnumSet.of(
            ClaimStatus.FILED, ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED, ClaimStatus.APPEALED);

    private final ClaimRepository claimRepository;
    private final ClaimFlagRepository flagRepository;
    private final AuditService auditService;
    private final CurrentActor currentActor;
    private final Clock clock;

    /** A weekly wage above this gets a second look. $4,000/week is about $208,000 a year. */
    @Value("${fraud.high-wage-threshold:4000.00}")
    private BigDecimal highWageThreshold;

    public FraudCheckService(ClaimRepository claimRepository, ClaimFlagRepository flagRepository,
                             AuditService auditService, CurrentActor currentActor, Clock clock) {
        this.claimRepository = claimRepository;
        this.flagRepository = flagRepository;
        this.auditService = auditService;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    /** Runs every check on a newly filed claim and saves a flag for each one that fails. */
    @Transactional
    public List<ClaimFlag> check(Claim claim) {
        List<ClaimFlag> raised = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock);

        List<Claim> sameEmail = claimRepository.findAllByClaimantEmailIgnoreCaseAndStatusInAndIdNot(
                claim.getClaimantEmail(), OPEN_STATUSES, claim.getId());
        if (!sameEmail.isEmpty()) {
            raised.add(new ClaimFlag(claim.getId(), FlagReason.DUPLICATE_OPEN_CLAIM,
                    "Open claim(s) with the same email: " + ids(sameEmail), now));
        }

        List<Claim> sameNameAndEmployer = claimRepository
                .findAllByClaimantNameIgnoreCaseAndEmployerNameIgnoreCaseAndStatusInAndIdNot(
                        claim.getClaimantName().trim(), claim.getEmployerName().trim(), OPEN_STATUSES, claim.getId())
                .stream()
                .filter(other -> !other.getClaimantEmail().equalsIgnoreCase(claim.getClaimantEmail()))
                .toList();
        if (!sameNameAndEmployer.isEmpty()) {
            raised.add(new ClaimFlag(claim.getId(), FlagReason.POSSIBLE_IDENTITY_REUSE,
                    "Open claim(s) with the same name and employer but a different email: "
                            + ids(sameNameAndEmployer), now));
        }

        if (claim.getAverageWeeklyWage().compareTo(highWageThreshold) > 0) {
            raised.add(new ClaimFlag(claim.getId(), FlagReason.HIGH_WAGE,
                    "Reported $" + claim.getAverageWeeklyWage() + "/week, above the $" + highWageThreshold
                            + " review threshold", now));
        }

        for (ClaimFlag flag : raised) {
            flagRepository.save(flag);
            auditService.recordAs("system", claim.getId(), ClaimEventType.FLAG_RAISED, null, null,
                    flag.getReason().getLabel() + ". " + flag.getDetails());
        }
        return raised;
    }

    @Transactional(readOnly = true)
    public List<ClaimFlag> flagsFor(Long claimId) {
        return flagRepository.findAllByClaimIdOrderByRaisedAtAsc(claimId);
    }

    @Transactional(readOnly = true)
    public boolean hasOpenFlags(Long claimId) {
        return flagRepository.countByClaimIdAndResolvedAtIsNull(claimId) > 0;
    }

    @Transactional(readOnly = true)
    public Set<Long> claimIdsWithOpenFlags() {
        return flagRepository.findClaimIdsWithOpenFlags();
    }

    /** A staff member has looked into a flag and explains why it's OK (or not). */
    @Transactional
    public ClaimFlag resolve(Long claimId, Long flagId, String note) {
        ClaimFlag flag = flagRepository.findByIdAndClaimId(flagId, claimId)
                .orElseThrow(() -> new InvalidClaimActionException("No such flag on this claim."));
        if (!flag.isOpen()) {
            throw new InvalidClaimActionException("This flag was already cleared.");
        }
        if (note == null || note.isBlank()) {
            throw new InvalidClaimActionException("Add a note explaining what you checked.");
        }
        flag.resolve(currentActor.name(), note.trim(), LocalDateTime.now(clock));
        ClaimFlag saved = flagRepository.save(flag);
        auditService.record(claimId, ClaimEventType.FLAG_CLEARED, null, null,
                flag.getReason().getLabel() + ". " + note.trim());
        return saved;
    }

    private static String ids(List<Claim> claims) {
        return String.join(", ", claims.stream().map(c -> "#" + c.getId()).toList());
    }
}
