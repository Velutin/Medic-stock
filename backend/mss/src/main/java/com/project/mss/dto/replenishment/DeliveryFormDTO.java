package com.project.mss.dto.replenishment;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Moves lots from the storeroom into the hospital and creates a delivery (with a PDF report). */
public record DeliveryFormDTO(
        @NotNull Long hospitalId,
        @NotEmpty @Valid List<DeliveryRequestItem> items,
        String notes
) {
    public record DeliveryRequestItem(@NotNull Long lotId, @NotNull @Positive Integer quantity) { }
}
