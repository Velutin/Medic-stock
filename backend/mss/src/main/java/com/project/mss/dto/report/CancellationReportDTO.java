package com.project.mss.dto.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cancelled surgeries in the period (by surgery date), grouped by who recorded them, with the share over everything
 * that person recorded in the period, to guide the use of the system.
 */
public record CancellationReportDTO(int launched, int cancelled, List<UserRow> byUser, List<Line> surgeries) {
    public record UserRow(String user, int launched, int cancelled) { }
    public record Line(Long surgeryId, LocalDate surgeryDate, String hospital, String patientName, String createdBy,
                       String reason, String cancelledBy, LocalDateTime cancelledAt, boolean hasSheet) { }
}
