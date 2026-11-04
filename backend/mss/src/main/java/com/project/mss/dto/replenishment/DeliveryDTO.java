package com.project.mss.dto.replenishment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.project.mss.model.entity.Delivery;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.enums.ShipmentStatus;

/** sourceHospitalId/sourceHospital are set when the material came from a distribution center. */
public record DeliveryDTO(Long id, Long hospitalId, String hospital, Long sourceHospitalId, String sourceHospital,
                          String notes, ShipmentStatus status, LocalDate arrivedOn, String arrivedBy,
                          LocalDateTime createdAt, List<DeliveryLine> items) {

    /** arrivedQuantity is null while the shipment is on the road, and what was in the box after that. */
    public record DeliveryLine(Long itemId, String ref, String description, String lot, LocalDate expiryDate,
                               int quantity, Integer arrivedQuantity) { }

    public static DeliveryDTO of(Delivery e) {
        Hospital source = e.getSourceHospital();
        return new DeliveryDTO(e.getId(), e.getHospital().getId(), e.getHospital().getName(),
                source == null ? null : source.getId(), source == null ? null : source.getName(),
                e.getNotes(), e.getStatus(), e.getArrivedOn(),
                e.getArrivedBy() == null ? null : e.getArrivedBy().getName(), e.getCreatedAt(),
                e.getItems().stream().map(i -> new DeliveryLine(i.getId(), i.getLot().getMaterial().getRef(),
                        i.getLot().getMaterial().getDescription(), i.getLot().getNumber(), i.getLot().getExpiryDate(),
                        i.getQuantity(), i.getArrivedQuantity())).toList());
    }
}
