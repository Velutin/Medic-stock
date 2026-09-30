package com.project.mss.dto.surgery;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.project.mss.model.entity.SurgeryItem;
import com.project.mss.model.enums.ReadSource;

public record SurgeryItemDTO(Long id, String ref, String description, String lot, LocalDate expiryDate,
                              int quantity, BigDecimal unitValue, ReadSource readSource) {
    public static SurgeryItemDTO of(SurgeryItem i) {
        return new SurgeryItemDTO(i.getId(), i.getLot().getMaterial().getRef(), i.getLot().getMaterial().getDescription(),
                i.getLot().getNumber(), i.getLot().getExpiryDate(), i.getQuantity(), i.getUnitValue(),
                i.getReadSource());
    }
}
