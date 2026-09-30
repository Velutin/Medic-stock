package com.project.mss.dto.replenishment;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Reviewed supplier order (non-urgent items may be removed before generating it). */
public record SupplierOrderFormDTO(
        @NotNull Long hospitalId,
        @NotEmpty @Valid List<SupplierOrderRequestItem> items,
        String notes
) {
    public record SupplierOrderRequestItem(@NotNull Long materialId, @NotNull @Positive Integer quantity, Boolean urgent) { }
}
