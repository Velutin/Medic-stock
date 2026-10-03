package com.project.mss.dto.stock;

import java.time.LocalDateTime;

import com.project.mss.model.entity.StockMovement;
import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.MovementType;

public record StockMovementDTO(Long id, MovementType type, String ref, String lot, int quantity,
                              String sourceHospital, Location sourceLocation,
                              String destinationHospital, Location destinationLocation,
                              Long surgeryId, Long loanId, Long deliveryId,
                              String user, String notes, LocalDateTime date) {
    public static StockMovementDTO of(StockMovement m) {
        return new StockMovementDTO(m.getId(), m.getType(), m.getLot().getMaterial().getRef(), m.getLot().getNumber(),
                m.getQuantity(),
                m.getSourceHospital() != null ? m.getSourceHospital().getName() : null, m.getSourceLocation(),
                m.getDestinationHospital() != null ? m.getDestinationHospital().getName() : null, m.getDestinationLocation(),
                m.getSurgeryId(), m.getLoanId(), m.getDeliveryId(),
                m.getUser() != null ? m.getUser().getName() : null, m.getNotes(), m.getCreatedAt());
    }
}
