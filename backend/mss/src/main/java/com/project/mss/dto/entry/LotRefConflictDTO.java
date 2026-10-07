package com.project.mss.dto.entry;

import java.time.LocalDate;
import java.util.List;

import com.project.mss.model.enums.Location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A lot number belongs to only one REF. For a material + lot number about to enter, the lots with the same number
 * registered with other REFs; they must become this REF before the entry (refused when usedInSurgery).
 */
public record LotRefConflictDTO(
        Long materialId,
        String lot,
        List<ExistingLot> lots
) {
    /** Material + lot number to check. */
    public record Check(@NotNull(message = "Material is required") Long materialId,
                        @NotBlank(message = "Lot is required") String lot) { }

    public record ExistingLot(Long lotId, Long materialId, String ref, String description, LocalDate expiryDate,
                              int units, boolean usedInSurgery, List<Balance> balances, List<SurgeryUse> surgeries) { }

    public record Balance(String hospital, Location location, int quantity) { }

    /**
     * Surgery where this lot was withdrawn, so the administrator can check what happened before fixing the REF.
     * Cancelled surgeries are not listed: they do not block the change. hasSheet tells whether the consumption
     * sheet is attached and can be opened at GET /surgeries/{surgeryId}/sheet.
     */
    public record SurgeryUse(Long surgeryId, LocalDate surgeryDate, String hospital, String patient,
                             String status, int quantity, boolean hasSheet) { }
}
