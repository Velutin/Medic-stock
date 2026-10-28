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

    /**
     * Pending issues of the given hospitals in [start, end] (reports).
     *
     * LEFT JOIN on the surgery, and the date compared by COALESCE: an entry issue has no surgery, and an
     * inner join would leave it out of the report while the dashboard card still counted it - a card and a
     * report disagreeing is worse than either being wrong on its own.
     */
    @Query("""
           SELECT p FROM PendingIssue p
           LEFT JOIN FETCH p.surgery c
           LEFT JOIN FETCH p.stockEntryItem si
           LEFT JOIN FETCH si.stockEntry
           JOIN FETCH p.hospital
           WHERE p.hospital.id IN :hospitalIds
             AND COALESCE(c.surgeryDate, CAST(p.createdAt AS date)) BETWEEN :start AND :end
           ORDER BY COALESCE(c.surgeryDate, CAST(p.createdAt AS date)) DESC, p.id DESC
           """)
    List<PendingIssue> listForReport(@Param("hospitalIds") java.util.Collection<Long> hospitalIds,
                                     @Param("start") java.time.LocalDate start,
                                     @Param("end") java.time.LocalDate end);

    /** Open issues raised by the items of one entry, to drop them when the entry is corrected. */
    @Query("""
           SELECT p FROM PendingIssue p
           WHERE p.stockEntryItem.stockEntry.id = :entryId AND p.status = :status
           """)
    List<PendingIssue> ofEntry(@Param("entryId") Long entryId, @Param("status") PendingIssueStatus status);
}
