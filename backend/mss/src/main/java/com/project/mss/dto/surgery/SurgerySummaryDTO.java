package com.project.mss.dto.surgery;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.project.mss.model.entity.Surgery;
import com.project.mss.model.enums.SurgeryStatus;

/** totalValue is only filled for administrators. surgicalTech: the person the surgery was recorded for. */
public record SurgerySummaryDTO(Long id, String hospital, String patientName, LocalDate surgeryDate,
                                String surgicalTech, SurgeryStatus status, BigDecimal totalValue, boolean hasSheet) {
    public static SurgerySummaryDTO of(Surgery c, boolean showValues) {
        return new SurgerySummaryDTO(c.getId(), c.getHospital().getName(), c.getPatientName(), c.getSurgeryDate(),
                c.getSurgicalTech() != null ? c.getSurgicalTech().getName() : null,
                c.getStatus(), showValues ? c.getTotalValue() : null, c.getSheetFile() != null);
    }
}
