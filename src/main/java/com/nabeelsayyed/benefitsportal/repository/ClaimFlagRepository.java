package com.nabeelsayyed.benefitsportal.repository;

import com.nabeelsayyed.benefitsportal.model.ClaimFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ClaimFlagRepository extends JpaRepository<ClaimFlag, Long> {
    List<ClaimFlag> findAllByClaimIdOrderByRaisedAtAsc(Long claimId);
    Optional<ClaimFlag> findByIdAndClaimId(Long id, Long claimId);
    long countByClaimIdAndResolvedAtIsNull(Long claimId);

    @Query("select distinct f.claimId from ClaimFlag f where f.resolvedAt is null")
    Set<Long> findClaimIdsWithOpenFlags();
}
