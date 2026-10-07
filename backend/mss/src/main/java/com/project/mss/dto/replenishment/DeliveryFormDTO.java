package com.project.mss.dto.replenishment;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Moves lots from the storeroom into the hospital and creates a delivery (with a PDF report).
 * sourceHospitalId (optional): storeroom owner the lots come from. When empty, the system uses the
 * distribution center that supplies the hospital (e.g. SESAB) or, for regular hospitals, the hospital itself.
 */
public record DeliveryFormDTO(
        @NotNull Long hospitalId,
        Long sourceHospitalId,
        @NotEmpty @Valid List<DeliveryRequestItem> items,
        String notes
) {
    public record DeliveryRequestItem(@NotNull Long lotId, @NotNull @Positive Integer quantity) { }
}
