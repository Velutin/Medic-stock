package com.project.mss.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.mss.model.entity.PendingTracking;

public interface PendingTrackingRepository extends JpaRepository<PendingTracking, Long> {

    /** Codes still waiting for an invoice with this number. */
    List<PendingTracking> findByNumberAndAppliedAtIsNull(String number);

    boolean existsByNumberAndTrackingCode(String number, String trackingCode);

    List<PendingTracking> findByAppliedAtIsNullOrderByCreatedAtDesc();
}
