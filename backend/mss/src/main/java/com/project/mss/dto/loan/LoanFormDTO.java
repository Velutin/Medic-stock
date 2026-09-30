package com.project.mss.dto.loan;

import java.util.List;

import com.project.mss.model.enums.Location;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Loan between hospitals. The source can be the stock inside the hospital (HOSPITAL)
 * or storeroom material assigned to it (STOREROOM). The material arrives inside the destination hospital.
 */
public record LoanFormDTO(
        @NotNull Long sourceHospitalId,
        @NotNull Location sourceLocation,
        @NotNull Long destinationHospitalId,
        @NotEmpty @Valid List<LoanRequestItem> items,
        String notes
) {
    public record LoanRequestItem(@NotNull Long lotId, @NotNull @Positive Integer quantity) { }
}
