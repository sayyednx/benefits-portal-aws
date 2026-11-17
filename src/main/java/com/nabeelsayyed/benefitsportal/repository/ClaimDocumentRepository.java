package com.nabeelsayyed.benefitsportal.repository;

import com.nabeelsayyed.benefitsportal.model.ClaimDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, Long> {
    List<ClaimDocument> findAllByClaimIdOrderByUploadedAtDesc(Long claimId);
    Optional<ClaimDocument> findByIdAndClaimId(Long id, Long claimId);
    long countByClaimId(Long claimId);
}
