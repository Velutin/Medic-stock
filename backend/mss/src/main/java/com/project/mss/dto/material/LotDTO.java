package com.project.mss.dto.material;

import java.time.LocalDate;

import com.project.mss.model.entity.Lot;

public record LotDTO(Long id, Long materialId, String ref, String description, String number,
                      LocalDate expiryDate, boolean expired) {
    public static LotDTO of(Lot l) {
        return new LotDTO(l.getId(), l.getMaterial().getId(), l.getMaterial().getRef(), l.getMaterial().getDescription(),
                l.getNumber(), l.getExpiryDate(), l.isExpired(LocalDate.now()));
    }
}
