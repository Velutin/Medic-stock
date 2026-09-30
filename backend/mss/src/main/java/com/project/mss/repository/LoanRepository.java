package com.project.mss.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.mss.model.entity.Loan;
import com.project.mss.model.enums.LoanStatus;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    List<Loan> findByStatusOrderByCreatedAtDesc(LoanStatus status);
    List<Loan> findTop50ByOrderByCreatedAtDesc();
}
