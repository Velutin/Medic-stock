package com.project.mss.dto.surgery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.project.mss.model.entity.Surgery;
import com.project.mss.model.enums.SurgeryStatus;

public record SurgeryDTO(Long id, Long hospitalId, String hospital, String patientName, LocalDate surgeryDate,
                          String doctor, String surgicalTech, SurgeryStatus status, BigDecimal totalValue,
                          boolean hasSheet, String notes, List<SurgeryItemDTO> items,
                          List<PendingIssueDTO> pendingIssues) {
    public static SurgeryDTO of(Surgery c, List<PendingIssueDTO> pendingIssues) {
        return new SurgeryDTO(c.getId(), c.getHospital().getId(), c.getHospital().getName(), c.getPatientName(),
                c.getSurgeryDate(), c.getDoctor(),
                c.getSurgicalTech() != null ? c.getSurgicalTech().getUsername() : null,
                c.getStatus(), c.getTotalValue(), c.getSheetFile() != null, c.getNotes(),
                c.getItems().stream().map(SurgeryItemDTO::of).toList(), pendingIssues);
    }
}
