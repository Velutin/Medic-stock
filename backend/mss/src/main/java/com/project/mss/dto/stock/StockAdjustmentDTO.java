package com.project.mss.dto.stock;

import com.project.mss.model.enums.Location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Inventory adjustment: sets the counted balance of a lot. */
public record StockAdjustmentDTO(
        @NotNull Long hospitalId,
        @NotNull Location location,
        @NotNull Long lotId,
        @NotNull @PositiveOrZero Integer countedQuantity,
        @NotBlank(message = "Adjustment reason is required") String reason
) { }
