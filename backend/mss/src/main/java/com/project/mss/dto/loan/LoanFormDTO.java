package com.project.mss.dto.loan;

import java.util.List;

import com.project.mss.model.enums.LoanType;
import com.project.mss.model.enums.Location;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Loan between hospitals (type LOAN, the default) or return to the supplier (type RETURN).
 * The source can be the stock inside the hospital (HOSPITAL) or storeroom material assigned to it (STOREROOM).
 * LOAN: destinationHospitalId is required and the material arrives inside the destination hospital.
 * RETURN: no destination; returnReason is required; expired lots may be returned.
 */
public record LoanFormDTO(
        LoanType type,
        @NotNull Long sourceHospitalId,
        @NotNull Location sourceLocation,
        Long destinationHospitalId,
        @NotEmpty @Valid List<LoanRequestItem> items,
        String notes,
        @Size(max = 1000) String returnReason
) {
    public record LoanRequestItem(@NotNull Long lotId, @NotNull @Positive Integer quantity) { }

    public LoanType typeOrLoan() {
        return type == null ? LoanType.LOAN : type;
    }
}
