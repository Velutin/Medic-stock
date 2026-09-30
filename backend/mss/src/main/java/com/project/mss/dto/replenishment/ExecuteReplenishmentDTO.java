package com.project.mss.dto.replenishment;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Moves lots from the storeroom into the hospital and creates a delivery (with a PDF report). */
public record ExecuteReplenishmentDTO(
        @NotNull Long hospitalId,
        @NotEmpty @Valid List<ReplenishmentRequestItem> items,
        String notes
) {
    public record ReplenishmentRequestItem(@NotNull Long lotId, @NotNull @Positive Integer quantity) { }
}
