package com.project.mss.dto.report;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.project.mss.model.enums.PendingIssueReason;
import com.project.mss.model.enums.PendingIssueStatus;

/** Item left as a pending issue at a surgery withdrawal, with the surgery and who recorded it. */
public record PendingIssueReportDTO(Long id, Long surgeryId, LocalDate surgeryDate, String patientName, Long hospitalId,
                                    String hospital, String enteredCode, String enteredRef, int quantity,
                                    PendingIssueReason reason, String createdBy, PendingIssueStatus status,
                                    String resolution, String resolvedBy, LocalDateTime createdAt) { }
