package com.project.mss.dto.replenishment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.project.mss.model.entity.StoreroomReturn;

/** Withdrawal from a hospital back into the storeroom. storeroomHospital is where the material went back to. */
public record StoreroomReturnDTO(Long id, Long hospitalId, String hospital, Long storeroomHospitalId,
                                 String storeroomHospital, String reason, String notes, String createdBy,
                                 LocalDateTime createdAt, List<StoreroomReturnLine> items) {

    public record StoreroomReturnLine(String ref, String description, String lot, LocalDate expiryDate,
                                      boolean expired, int quantity) { }

    public static StoreroomReturnDTO of(StoreroomReturn e) {
        LocalDate day = e.getCreatedAt() == null ? LocalDate.now() : e.getCreatedAt().toLocalDate();
        return new StoreroomReturnDTO(e.getId(), e.getHospital().getId(), e.getHospital().getName(),
                e.getStoreroomHospital().getId(), e.getStoreroomHospital().getName(),
                e.getReason(), e.getNotes(),
                e.getCreatedBy() == null ? null : e.getCreatedBy().getName(), e.getCreatedAt(),
                e.getItems().stream().map(i -> new StoreroomReturnLine(i.getLot().getMaterial().getRef(),
                        i.getLot().getMaterial().getDescription(), i.getLot().getNumber(),
                        i.getLot().getExpiryDate(), i.getLot().isExpired(day), i.getQuantity())).toList());
    }
}
