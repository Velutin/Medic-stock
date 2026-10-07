package com.project.mss.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

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

    /** Latest movements involving any of the given hospitals. */
    @Query("""
           SELECT m FROM StockMovement m
           WHERE m.sourceHospital.id IN :hospitalIds OR m.destinationHospital.id IN :hospitalIds
           ORDER BY m.createdAt DESC
           """)
    List<StockMovement> latest(@Param("hospitalIds") Collection<Long> hospitalIds, Pageable pageable);

    /** Movements involving any of the given hospitals in [start, end] (reports). */
    @Query("""
           SELECT m FROM StockMovement m
           WHERE (m.sourceHospital.id IN :hospitalIds OR m.destinationHospital.id IN :hospitalIds)
             AND m.createdAt BETWEEN :start AND :end
           ORDER BY m.createdAt DESC
           """)
    List<StockMovement> listForReport(@Param("hospitalIds") Collection<Long> hospitalIds,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end,
                                      Pageable pageable);
}
