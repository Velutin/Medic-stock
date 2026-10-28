package com.project.mss.dto.report;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.project.mss.model.enums.PendingIssueReason;
import com.project.mss.model.enums.PendingIssueStatus;

/**
 * A pending issue in the report, from a surgery withdrawal or from an entry without an invoice.
 *
 * surgeryId, surgeryDate and patientName are null for an entry issue, and entryId is null for a surgery one.
 * date is the one the report sorts and filters by, and it is always filled: the surgery date when there is a
 * surgery, the day the issue was raised otherwise - a column that is empty for half the rows would make the
 * report unreadable exactly where it matters.
 */
public record PendingIssueReportDTO(Long id, Long surgeryId, LocalDate surgeryDate, String patientName, Long hospitalId,
                                    String hospital, String enteredCode, String enteredRef, int quantity,
                                    PendingIssueReason reason, String createdBy, PendingIssueStatus status,
                                    String resolution, String resolvedBy, Long entryId, String declaredInvoice,
                                    LocalDate date, LocalDateTime createdAt) { }
