package com.nabeelsayyed.benefitsportal.repository;

import com.nabeelsayyed.benefitsportal.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findAllByClaimIdOrderByWeekEndingDesc(Long claimId);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.claimId = :claimId")
    BigDecimal totalPaid(Long claimId);
}
