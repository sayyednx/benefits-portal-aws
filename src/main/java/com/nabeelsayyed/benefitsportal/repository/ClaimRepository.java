package com.nabeelsayyed.benefitsportal.repository;

import com.nabeelsayyed.benefitsportal.model.Claim;
import com.nabeelsayyed.benefitsportal.model.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
    List<Claim> findAllByStatusOrderByFiledAtDesc(ClaimStatus status);
    List<Claim> findAllByOrderByFiledAtDesc();
    Optional<Claim> findByConfirmationNumber(String confirmationNumber);
    boolean existsByConfirmationNumber(String confirmationNumber);
}
