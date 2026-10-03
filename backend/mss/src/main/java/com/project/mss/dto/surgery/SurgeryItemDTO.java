package com.project.mss.dto.surgery;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.project.mss.model.entity.SurgeryItem;
import com.project.mss.model.enums.ReadSource;

/**
 * unitValue and missingPrice are only filled for administrators (surgical techs do not see values).
 * missingPrice: the hospital had no value for the REF; it is filled automatically when the value is registered.
 */
public record SurgeryItemDTO(Long id, String ref, String component, String description, String lot,
                             LocalDate expiryDate, int quantity, BigDecimal unitValue, Boolean missingPrice,
                             ReadSource readSource) {
    public static SurgeryItemDTO of(SurgeryItem i, boolean showValues) {
        var m = i.getLot().getMaterial();
        return new SurgeryItemDTO(i.getId(), m.getRef(), m.getComponent(), m.getDescription(),
                i.getLot().getNumber(), i.getLot().getExpiryDate(), i.getQuantity(),
                showValues ? i.getUnitValue() : null,
                showValues ? i.getUnitValue() == null : null,
                i.getReadSource());
    }
}
