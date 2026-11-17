package com.nabeelsayyed.benefitsportal.repository;

import com.nabeelsayyed.benefitsportal.model.ClaimEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimEventRepository extends JpaRepository<ClaimEvent, Long> {
    List<ClaimEvent> findAllByClaimIdOrderByOccurredAtAscIdAsc(Long claimId);
}
