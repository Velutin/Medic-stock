package com.project.mss.dto.loan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.project.mss.model.entity.Loan;
import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.LoanType;
import com.project.mss.model.enums.ShipmentStatus;
import com.project.mss.model.entity.Hospital;

/**
 * Loan between hospitals (type LOAN) or return to the supplier (type RETURN: no destination, returnReason filled).
 */
public record LoanDTO(Long id, LoanType type, Long sourceHospitalId, String sourceHospital, Location sourceLocation,
                      Long destinationHospitalId, String destinationHospital, String notes,
                      String returnReason, String createdBy, ShipmentStatus status, LocalDate arrivedOn,
                      String arrivedBy, LocalDateTime createdAt,
                      List<LoanLine> items) {
    /** arrivedQuantity is null while the shipment is on the road, and what was in the box after that. */
    public record LoanLine(Long itemId, String ref, String description, String lot, LocalDate expiryDate,
                           int quantity, Integer arrivedQuantity) { }
    public static LoanDTO of(Loan e) {
        Hospital dest = e.getDestinationHospital();
        return new LoanDTO(e.getId(), e.getType(), e.getSourceHospital().getId(), e.getSourceHospital().getName(),
                e.getSourceLocation(), dest == null ? null : dest.getId(), dest == null ? null : dest.getName(),
                e.getNotes(), e.getReturnReason(),
                e.getCreatedBy() == null ? null : e.getCreatedBy().getName(),
                e.getStatus(), e.getArrivedOn(), e.getArrivedBy() == null ? null : e.getArrivedBy().getName(),
                e.getCreatedAt(),
                e.getItems().stream().map(i -> new LoanLine(i.getId(), i.getLot().getMaterial().getRef(),
                        i.getLot().getMaterial().getDescription(), i.getLot().getNumber(),
                        i.getLot().getExpiryDate(), i.getQuantity(), i.getArrivedQuantity())).toList());
    }
}
