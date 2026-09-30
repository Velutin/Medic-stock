package com.project.mss.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.mss.model.entity.PendingIssue;
import com.project.mss.model.enums.PendingIssueStatus;

public interface PendingIssueRepository extends JpaRepository<PendingIssue, Long> {
    List<PendingIssue> findByStatusOrderByCreatedAtDesc(PendingIssueStatus status);
    List<PendingIssue> findBySurgeryIdOrderByIdAsc(Long surgeryId);
    long countBySurgeryIdAndStatus(Long surgeryId, PendingIssueStatus status);
}
