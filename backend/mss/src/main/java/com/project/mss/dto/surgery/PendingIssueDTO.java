package com.project.mss.dto.surgery;

import java.time.LocalDateTime;

import com.project.mss.model.entity.PendingIssue;
import com.project.mss.model.enums.PendingIssueReason;
import com.project.mss.model.enums.ReadSource;
import com.project.mss.model.enums.PendingIssueStatus;

public record PendingIssueDTO(Long id, Long surgeryId, Long hospitalId, String hospital, String enteredCode,
                           String enteredRef, int quantity, ReadSource readSource,
                           PendingIssueReason reason, PendingIssueStatus status, String resolution, LocalDateTime createdAt) {
    public static PendingIssueDTO of(PendingIssue p) {
        return new PendingIssueDTO(p.getId(), p.getSurgery() != null ? p.getSurgery().getId() : null,
                p.getHospital().getId(), p.getHospital().getName(), p.getEnteredCode(), p.getEnteredRef(),
                p.getQuantity(), p.getReadSource(), p.getReason(), p.getStatus(), p.getResolution(),
                p.getCreatedAt());
    }
}
