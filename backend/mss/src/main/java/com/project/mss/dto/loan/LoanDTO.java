package com.project.mss.dto.loan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.project.mss.model.entity.Loan;
import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.LoanType;
import com.project.mss.model.entity.Hospital;

/**
 * Loan between hospitals (type LOAN) or return to the supplier (type RETURN: no destination, returnReason filled).
 */
public record LoanDTO(Long id, LoanType type, Long sourceHospitalId, String sourceHospital, Location sourceLocation,
                      Long destinationHospitalId, String destinationHospital, String notes,
                      String returnReason, String createdBy, LocalDateTime createdAt,
                      List<LoanLine> items) {
    public record LoanLine(String ref, String description, String lot, LocalDate expiryDate, int quantity) { }
    public static LoanDTO of(Loan e) {
        Hospital dest = e.getDestinationHospital();
        return new LoanDTO(e.getId(), e.getType(), e.getSourceHospital().getId(), e.getSourceHospital().getName(),
                e.getSourceLocation(), dest == null ? null : dest.getId(), dest == null ? null : dest.getName(),
                e.getNotes(), e.getReturnReason(),
                e.getCreatedBy() == null ? null : e.getCreatedBy().getName(), e.getCreatedAt(),
                e.getItems().stream().map(i -> new LoanLine(i.getLot().getMaterial().getRef(),
                        i.getLot().getMaterial().getDescription(), i.getLot().getNumber(),
                        i.getLot().getExpiryDate(), i.getQuantity())).toList());
    }
}
