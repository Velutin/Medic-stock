package com.project.mss.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import com.project.mss.model.entity.Delivery;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    List<Delivery> findTop50ByHospitalIdOrderByCreatedAtDesc(Long hospitalId);

    List<Delivery> findTop50BySourceHospitalIdOrderByCreatedAtDesc(Long sourceHospitalId);

    /** Deliveries to (or from the storeroom of) the given hospitals created in [start, end] (reports). */
    @Query("""
           SELECT d FROM Delivery d
           WHERE (d.hospital.id IN :hospitalIds OR d.sourceHospital.id IN :hospitalIds)
             AND d.createdAt BETWEEN :start AND :end
           ORDER BY d.createdAt DESC
           """)
    List<Delivery> listForReport(@Param("hospitalIds") java.util.Collection<Long> hospitalIds,
                                 @Param("start") java.time.LocalDateTime start,
                                 @Param("end") java.time.LocalDateTime end);
}
