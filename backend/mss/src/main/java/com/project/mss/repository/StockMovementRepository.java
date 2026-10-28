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

    /**
     * Movements for the report, with the filters the screen offers.
     *
     * A lot or an invoice turns the question from "what happened in this period" into "where did this lot
     * go", which has no date: the period is then ignored, by passing a range that holds everything. The lot
     * is compared without leading zeros and as a prefix, so 005706061 finds 5706061 and 5706061B - the same
     * physical lot written in the three ways the labels write it.
     *
     * lotNumber is never null: no filter is the empty string, which becomes LIKE '%' and matches every lot.
     * Comparing the parameter with IS NULL leaves Hibernate without a type for it, and it binds the value as
     * binary - the query then fails in PostgreSQL with btrim(bytea) does not exist, not at compile time.
     */
    @Query("""
           SELECT m FROM StockMovement m
           WHERE (m.sourceHospital.id IN :hospitalIds OR m.destinationHospital.id IN :hospitalIds)
             AND m.createdAt BETWEEN :start AND :end
             AND TRIM(LEADING '0' FROM UPPER(TRIM(m.lot.number)))
                 LIKE CONCAT(TRIM(LEADING '0' FROM UPPER(TRIM(:lotNumber))), '%')
           ORDER BY m.createdAt DESC
           """)
    List<StockMovement> listForReport(@Param("hospitalIds") Collection<Long> hospitalIds,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end,
                                      @Param("lotNumber") String lotNumber,
                                      Pageable pageable);

    /**
     * The same report restricted to given lots, which is how a search by invoice works: the invoice resolves
     * to the lots it brought in and the movements of those lots are the answer.
     *
     * It is a method of its own instead of a nullable parameter on the one above because a collection
     * compared with IS NULL is not something JPQL handles - it fails at runtime instead of filtering.
     */
    @Query("""
           SELECT m FROM StockMovement m
           WHERE (m.sourceHospital.id IN :hospitalIds OR m.destinationHospital.id IN :hospitalIds)
             AND m.createdAt BETWEEN :start AND :end
             AND m.lot.id IN :lotIds
             AND TRIM(LEADING '0' FROM UPPER(TRIM(m.lot.number)))
                 LIKE CONCAT(TRIM(LEADING '0' FROM UPPER(TRIM(:lotNumber))), '%')
           ORDER BY m.createdAt DESC
           """)
    List<StockMovement> listForReportInLots(@Param("hospitalIds") Collection<Long> hospitalIds,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end,
                                            @Param("lotNumber") String lotNumber,
                                            @Param("lotIds") Collection<Long> lotIds,
                                            Pageable pageable);

}
