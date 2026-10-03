package com.project.mss.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.mss.model.entity.BillingRate;

public interface BillingRateRepository extends JpaRepository<BillingRate, Long> {

    List<BillingRate> findAllByOrderByValidFromDesc();

    Optional<BillingRate> findByValidFrom(LocalDate validFrom);

    /** Rate in effect on the given date (the latest one that started on or before it). */
    Optional<BillingRate> findFirstByValidFromLessThanEqualOrderByValidFromDesc(LocalDate date);
}
