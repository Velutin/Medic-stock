package com.project.mss.dto.loan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.project.mss.model.entity.Loan;
import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.LoanStatus;

public record LoanDTO(Long id, String sourceHospital, Location sourceLocation, String destinationHospital,
                            LoanStatus status, String notes, LocalDateTime createdAt,
                            LocalDateTime notifiedAt, List<LoanLine> items) {

    public record LoanLine(String ref, String description, String lot, LocalDate expiryDate, int quantity) { }

    public static LoanDTO of(Loan e) {
        return new LoanDTO(e.getId(), e.getSourceHospital().getName(), e.getSourceLocation(),
                e.getDestinationHospital().getName(), e.getStatus(), e.getNotes(), e.getCreatedAt(),
                e.getNotifiedAt(),
                e.getItems().stream().map(i -> new LoanLine(i.getLot().getMaterial().getRef(),
                        i.getLot().getMaterial().getDescription(), i.getLot().getNumber(),
                        i.getLot().getExpiryDate(), i.getQuantity())).toList());
    }
}
