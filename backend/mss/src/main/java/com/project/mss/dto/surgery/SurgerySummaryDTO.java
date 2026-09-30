package com.project.mss.dto.surgery;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.project.mss.model.entity.Surgery;
import com.project.mss.model.enums.SurgeryStatus;

public record SurgerySummaryDTO(Long id, String hospital, String patientName, LocalDate surgeryDate,
                                SurgeryStatus status, BigDecimal totalValue) {
    public static SurgerySummaryDTO of(Surgery c) {
        return new SurgerySummaryDTO(c.getId(), c.getHospital().getName(), c.getPatientName(), c.getSurgeryDate(),
                c.getStatus(), c.getTotalValue());
    }
}
