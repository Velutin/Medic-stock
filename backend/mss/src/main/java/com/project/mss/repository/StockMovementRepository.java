package com.project.mss.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.mss.model.entity.StockMovement;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("""
           SELECT m FROM StockMovement m
           WHERE (m.sourceHospital.id = :hospitalId OR m.destinationHospital.id = :hospitalId)
             AND m.createdAt BETWEEN :start AND :end
           ORDER BY m.createdAt DESC
           """)
    Page<StockMovement> listByHospital(@Param("hospitalId") Long hospitalId,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end,
                                         Pageable pageable);
}
