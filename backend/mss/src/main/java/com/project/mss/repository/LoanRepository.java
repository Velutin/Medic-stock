package com.project.mss.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import com.project.mss.model.entity.Loan;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    List<Loan> findTop50ByOrderByCreatedAtDesc();

    /** Loans or returns of the given type created in [start, end] (reports; hospital filter applied by the caller). */
    @Query("""
           SELECT e FROM Loan e
           WHERE e.type = :type AND e.createdAt BETWEEN :start AND :end
           ORDER BY e.createdAt DESC
           """)
    List<Loan> listForReport(@Param("type") com.project.mss.model.enums.LoanType type,
                             @Param("start") java.time.LocalDateTime start,
                             @Param("end") java.time.LocalDateTime end);
}
