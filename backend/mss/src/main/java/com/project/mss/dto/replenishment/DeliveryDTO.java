package com.project.mss.dto.replenishment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.project.mss.model.entity.Delivery;

public record DeliveryDTO(Long id, Long hospitalId, String hospital, String notes, LocalDateTime createdAt,
                         List<DeliveryLine> items) {

    public record DeliveryLine(String ref, String description, String lot, LocalDate expiryDate, int quantity) { }

    public static DeliveryDTO of(Delivery e) {
        return new DeliveryDTO(e.getId(), e.getHospital().getId(), e.getHospital().getName(), e.getNotes(),
                e.getCreatedAt(),
                e.getItems().stream().map(i -> new DeliveryLine(i.getLot().getMaterial().getRef(),
                        i.getLot().getMaterial().getDescription(), i.getLot().getNumber(), i.getLot().getExpiryDate(),
                        i.getQuantity())).toList());
    }
}
