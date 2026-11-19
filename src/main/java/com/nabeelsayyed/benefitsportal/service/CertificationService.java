package com.nabeelsayyed.benefitsportal.service;

import com.nabeelsayyed.benefitsportal.dto.CertificationRequest;
import com.nabeelsayyed.benefitsportal.exception.InvalidClaimActionException;
import com.nabeelsayyed.benefitsportal.model.*;
import com.nabeelsayyed.benefitsportal.repository.PaymentRepository;
import com.nabeelsayyed.benefitsportal.repository.WeeklyCertificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Weekly certifications and the payments they produce.
 *
 * Weeks run Sunday to Saturday, and each one is named by its last day (the "week ending"
 * Saturday). A week can be certified once it is over. The first week is the one the claim
 * was filed in.
 */
@Service
public class CertificationService {

    private final ClaimService claimService;
    private final WeeklyCertificationRepository certificationRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentCalculator calculator;
    private final AuditService auditService;
    private final Clock clock;

    /** How many weeks of full benefits one claim can pay in total. 26 is the most common. */
    @Value("${benefits.max-weeks:26}")
    private int maxWeeks;

    public CertificationService(ClaimService claimService, WeeklyCertificationRepository certificationRepository,
                                PaymentRepository paymentRepository, PaymentCalculator calculator,
                                AuditService auditService, Clock clock) {
        this.claimService = claimService;
        this.certificationRepository = certificationRepository;
        this.paymentRepository = paymentRepository;
        this.calculator = calculator;
        this.auditService = auditService;
        this.clock = clock;
    }

    public int getMaxWeeks() {
        return maxWeeks;
    }

    /** The most this claim can ever pay: weekly benefit x max weeks. */
    public BigDecimal maximumBenefit(Claim claim) {
        return claim.getWeeklyBenefitAmount().multiply(BigDecimal.valueOf(maxWeeks));
    }

    @Transactional(readOnly = true)
    public BigDecimal totalPaid(Long claimId) {
        return paymentRepository.totalPaid(claimId);
    }

    @Transactional(readOnly = true)
    public BigDecimal remainingBalance(Claim claim) {
        return maximumBenefit(claim).subtract(totalPaid(claim.getId())).max(BigDecimal.ZERO);
    }

    /** The Saturday ending the week the claim was filed in. */
    public LocalDate firstWeekEnding(Claim claim) {
        return claim.getFiledAt().toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
    }

    /** Finished weeks the claimant hasn't certified yet, oldest first. Empty unless approved. */
    @Transactional(readOnly = true)
    public List<LocalDate> weeksDue(Claim claim) {
        List<LocalDate> due = new ArrayList<>();
        if (claim.getStatus() != ClaimStatus.APPROVED || remainingBalance(claim).signum() == 0) {
            return due;
        }
        LocalDate today = LocalDate.now(clock);
        for (LocalDate week = firstWeekEnding(claim); week.isBefore(today); week = week.plusWeeks(1)) {
            if (!certificationRepository.existsByClaimIdAndWeekEnding(claim.getId(), week)) {
                due.add(week);
            }
        }
        return due;
    }

    @Transactional(readOnly = true)
    public List<WeeklyCertification> certificationsFor(Long claimId) {
        return certificationRepository.findAllByClaimIdOrderByWeekEndingDesc(claimId);
    }

    @Transactional(readOnly = true)
    public List<Payment> paymentsFor(Long claimId) {
        return paymentRepository.findAllByClaimIdOrderByWeekEndingDesc(claimId);
    }

    /** Saves one week's answers, works out the payment, and issues it if there is one. */
    @Transactional
    public WeeklyCertification certify(Long claimId, CertificationRequest request) {
        Claim claim = claimService.findById(claimId);
        LocalDate week = request.getWeekEnding();

        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new InvalidClaimActionException("You can certify for weeks once your claim is approved.");
        }
        if (week.getDayOfWeek() != DayOfWeek.SATURDAY) {
            throw new InvalidClaimActionException("Weeks end on a Saturday. Pick a week-ending date that is a Saturday.");
        }
        if (week.isBefore(firstWeekEnding(claim))) {
            throw new InvalidClaimActionException("That week is before your claim started.");
        }
        if (!week.isBefore(LocalDate.now(clock))) {
            throw new InvalidClaimActionException("You can only certify for a week after it has ended.");
        }
        if (certificationRepository.existsByClaimIdAndWeekEnding(claimId, week)) {
            throw new InvalidClaimActionException("You already certified for the week ending " + week + ".");
        }

        PaymentCalculator.WeekResult result = calculator.calculate(
                request.getAbleAndAvailable(), request.getRefusedWork(), request.getJobContacts(),
                request.getGrossEarnings(), claim.getWeeklyBenefitAmount(), remainingBalance(claim),
                certificationRepository.existsByClaimIdAndOutcome(claimId, CertificationOutcome.WAITING_WEEK));

        LocalDateTime now = LocalDateTime.now(clock);
        WeeklyCertification certification = certificationRepository.save(new WeeklyCertification(
                claimId, week, request.getAbleAndAvailable(), request.getRefusedWork(), request.getJobContacts(),
                request.getGrossEarnings(), result.outcome(), result.amount(), result.reason(), now));
        auditService.record(claimId, ClaimEventType.CERTIFICATION_SUBMITTED, null, null,
                "Week ending " + week + ": " + result.outcome() + ". " + result.reason());

        if (result.outcome() == CertificationOutcome.PAID) {
            paymentRepository.save(new Payment(claimId, certification.getId(), week, result.amount(), now));
            auditService.recordAs("system", claimId, ClaimEventType.PAYMENT_ISSUED, null, null,
                    "$" + result.amount() + " for the week ending " + week);
        }
        return certification;
    }
}
