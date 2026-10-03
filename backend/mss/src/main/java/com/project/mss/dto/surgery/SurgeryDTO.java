package com.project.mss.dto.surgery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.project.mss.model.entity.Surgery;
import com.project.mss.model.enums.SurgeryStatus;

/**
 * For cancelled surgeries, items and totalValue are a record only: the material was returned to stock.
 * totalValue and the item values are only filled for administrators (surgical techs do not see values).
 */
public record SurgeryDTO(Long id, Long hospitalId, String hospital, String patientName, LocalDate surgeryDate,
                         String doctor, Long surgicalTechId, String surgicalTech, String createdBy,
                         SurgeryStatus status, BigDecimal totalValue, boolean hasSheet, String notes,
                         LocalDateTime completedAt, LocalDateTime cancelledAt, String cancelledBy,
                         String cancellationReason, List<SurgeryItemDTO> items, List<PendingIssueDTO> pendingIssues) {
    public static SurgeryDTO of(Surgery c, List<PendingIssueDTO> pendingIssues, boolean showValues) {
        return new SurgeryDTO(c.getId(), c.getHospital().getId(), c.getHospital().getName(), c.getPatientName(),
                c.getSurgeryDate(), c.getDoctor(),
                c.getSurgicalTech() != null ? c.getSurgicalTech().getId() : null,
                c.getSurgicalTech() != null ? c.getSurgicalTech().getName() : null,
                c.getCreatedBy() != null ? c.getCreatedBy().getName() : null,
                c.getStatus(), showValues ? c.getTotalValue() : null, c.getSheetFile() != null, c.getNotes(),
                c.getCompletedAt(), c.getCancelledAt(),
                c.getCancelledBy() != null ? c.getCancelledBy().getName() : null,
                c.getCancellationReason(),
                c.getItems().stream().map(i -> SurgeryItemDTO.of(i, showValues)).toList(), pendingIssues);
    }
}
