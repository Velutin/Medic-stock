package com.project.mss.dto.stock;

import java.time.LocalDate;

import com.project.mss.model.enums.Location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Correction of a lot from the stock by lot: the number or the expiry date typed wrong at the entry, plus
 * the counted balance of the line being looked at. A lot is one record shared by every hospital, so the
 * number and the expiry date are corrected everywhere the lot appears - it is the same physical lot.
 * countedQuantity is optional: leaving it out corrects only the lot.
 */
public record LotCorrectionDTO(
        @NotBlank(message = "Lot is required") @Size(max = 80) String lot,
        @NotNull(message = "Expiry date is required") LocalDate expiryDate,
        @NotNull(message = "Hospital is required") Long hospitalId,
        @NotNull(message = "Location is required") Location location,
        Integer countedQuantity,
        @NotBlank(message = "Adjustment reason is required") @Size(max = 500) String reason
) { }
