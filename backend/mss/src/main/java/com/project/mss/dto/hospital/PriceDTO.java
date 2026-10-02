package com.project.mss.dto.hospital;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.project.mss.model.entity.HospitalPrice;

/**
 * Price of a REF for a hospital. sourceHospitalId/sourceHospital tell whose table the value comes from:
 * the hospital itself or, when it has no value of its own, the distribution center that supplies it (e.g. SESAB).
 */
public record PriceDTO(Long materialId, String ref, String description, BigDecimal value, LocalDateTime updatedAt,
                       Long sourceHospitalId, String sourceHospital) {
    public static PriceDTO of(HospitalPrice p) {
        return new PriceDTO(p.getMaterial().getId(), p.getMaterial().getRef(), p.getMaterial().getDescription(),
                p.getValue(), p.getUpdatedAt(), p.getHospital().getId(), p.getHospital().getName());
    }
}