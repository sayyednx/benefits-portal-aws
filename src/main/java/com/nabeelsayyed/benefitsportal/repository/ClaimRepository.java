package com.nabeelsayyed.benefitsportal.repository;

import com.nabeelsayyed.benefitsportal.model.Claim;
import com.nabeelsayyed.benefitsportal.model.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
    List<Claim> findAllByStatusOrderByFiledAtDesc(ClaimStatus status);
    List<Claim> findAllByOrderByFiledAtDesc();
    Optional<Claim> findByConfirmationNumber(String confirmationNumber);
    boolean existsByConfirmationNumber(String confirmationNumber);

    // Used by the fraud checks to find other open claims that look like this one.
    List<Claim> findAllByClaimantEmailIgnoreCaseAndStatusInAndIdNot(
            String email, Collection<ClaimStatus> statuses, Long excludeId);

    List<Claim> findAllByClaimantNameIgnoreCaseAndEmployerNameIgnoreCaseAndStatusInAndIdNot(
            String name, String employer, Collection<ClaimStatus> statuses, Long excludeId);
}
