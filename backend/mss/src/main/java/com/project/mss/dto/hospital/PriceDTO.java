package com.project.mss.dto.hospital;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.project.mss.model.entity.HospitalPrice;

public record PriceDTO(Long materialId, String ref, String description, BigDecimal value, LocalDateTime updatedAt) {
    public static PriceDTO of(HospitalPrice p) {
        return new PriceDTO(p.getMaterial().getId(), p.getMaterial().getRef(), p.getMaterial().getDescription(),
                p.getValue(), p.getUpdatedAt());
    }
}
