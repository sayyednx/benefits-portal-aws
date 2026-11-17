package com.nabeelsayyed.benefitsportal.repository;

import com.nabeelsayyed.benefitsportal.model.CertificationOutcome;
import com.nabeelsayyed.benefitsportal.model.WeeklyCertification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface WeeklyCertificationRepository extends JpaRepository<WeeklyCertification, Long> {
    List<WeeklyCertification> findAllByClaimIdOrderByWeekEndingDesc(Long claimId);
    boolean existsByClaimIdAndWeekEnding(Long claimId, LocalDate weekEnding);
    boolean existsByClaimIdAndOutcome(Long claimId, CertificationOutcome outcome);
}
