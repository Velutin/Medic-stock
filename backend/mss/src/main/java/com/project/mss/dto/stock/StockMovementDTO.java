package com.project.mss.dto.stock;

import java.time.LocalDateTime;

import com.project.mss.model.entity.StockMovement;
import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.MovementType;

/**
 * A stock movement as the reports show it.
 *
 * invoice is filled only on entry rows, which is where an invoice exists: it names the invoices that brought
 * the lot in that receipt. A withdrawal or a transfer carries no invoice of its own - the material came in
 * once and moved afterwards.
 */
public record StockMovementDTO(Long id, MovementType type, String ref, String lot, int quantity,
                              String sourceHospital, Location sourceLocation,
                              String destinationHospital, Location destinationLocation,
                              Long surgeryId, Long loanId, Long deliveryId, Long stockEntryId,
                              String user, String notes, LocalDateTime date, String invoice) {
    /** The same row naming the invoices of its entry. */
    public StockMovementDTO withInvoice(String invoiceLabel) {
        return new StockMovementDTO(id, type, ref, lot, quantity, sourceHospital, sourceLocation,
                destinationHospital, destinationLocation, surgeryId, loanId, deliveryId, stockEntryId,
                user, notes, date, invoiceLabel);
    }

    public static StockMovementDTO of(StockMovement m) {
        return new StockMovementDTO(m.getId(), m.getType(), m.getLot().getMaterial().getRef(), m.getLot().getNumber(),
                m.getQuantity(),
                m.getSourceHospital() != null ? m.getSourceHospital().getName() : null, m.getSourceLocation(),
                m.getDestinationHospital() != null ? m.getDestinationHospital().getName() : null, m.getDestinationLocation(),
                m.getSurgeryId(), m.getLoanId(), m.getDeliveryId(), m.getStockEntryId(),
                m.getUser() != null ? m.getUser().getName() : null, m.getNotes(), m.getCreatedAt(), null);
    }
}
