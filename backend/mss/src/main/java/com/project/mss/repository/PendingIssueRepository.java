package com.project.mss.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import com.project.mss.model.entity.PendingIssue;
import com.project.mss.model.enums.PendingIssueStatus;

public interface PendingIssueRepository extends JpaRepository<PendingIssue, Long> {
    List<PendingIssue> findByStatusOrderByCreatedAtDesc(PendingIssueStatus status);
    List<PendingIssue> findBySurgeryIdOrderByIdAsc(Long surgeryId);
    long countBySurgeryIdAndStatus(Long surgeryId, PendingIssueStatus status);
    long countByHospitalIdInAndStatus(java.util.Collection<Long> hospitalIds, PendingIssueStatus status);

    /** Oldest surgery date among the pending issues of the given hospitals and status (dashboard), null when none. */
    @Query("SELECT MIN(c.surgeryDate) FROM PendingIssue p JOIN p.surgery c WHERE p.hospital.id IN :hospitalIds AND p.status = :status")
    java.time.LocalDate oldestSurgeryDate(@Param("hospitalIds") java.util.Collection<Long> hospitalIds,
                                          @Param("status") PendingIssueStatus status);

    /** Latest surgery date among the pending issues of the given hospitals and status (dashboard), null when none. */
    @Query("SELECT MAX(c.surgeryDate) FROM PendingIssue p JOIN p.surgery c WHERE p.hospital.id IN :hospitalIds AND p.status = :status")
    java.time.LocalDate latestSurgeryDate(@Param("hospitalIds") java.util.Collection<Long> hospitalIds,
                                          @Param("status") PendingIssueStatus status);

    /** Pending issues of surgeries of the given hospitals with the surgery date in [start, end] (reports). */
    @Query("""
           SELECT p FROM PendingIssue p JOIN FETCH p.surgery c JOIN FETCH p.hospital
           WHERE p.hospital.id IN :hospitalIds AND c.surgeryDate BETWEEN :start AND :end
           ORDER BY c.surgeryDate DESC, p.id DESC
           """)
    List<PendingIssue> listForReport(@Param("hospitalIds") java.util.Collection<Long> hospitalIds,
                                     @Param("start") java.time.LocalDate start,
                                     @Param("end") java.time.LocalDate end);
}
