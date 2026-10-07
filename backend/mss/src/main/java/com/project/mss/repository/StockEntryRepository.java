package com.project.mss.repository;

import java.time.LocalDate;
import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.mss.model.entity.StockEntry;

public interface StockEntryRepository extends JpaRepository<StockEntry, Long> {

    /** Entries of the given hospitals in a period, newest first. */
    @Query(value = """
           SELECT e FROM StockEntry e
           WHERE e.hospital.id IN :hospitalIds
             AND e.entryDate BETWEEN :start AND :end
           ORDER BY e.entryDate DESC, e.id DESC
           """,
           countQuery = """
           SELECT COUNT(e) FROM StockEntry e
           WHERE e.hospital.id IN :hospitalIds
             AND e.entryDate BETWEEN :start AND :end
           """)
    Page<StockEntry> list(@Param("hospitalIds") Collection<Long> hospitalIds,
                          @Param("start") LocalDate start,
                          @Param("end") LocalDate end,
                          Pageable pageable);
}
