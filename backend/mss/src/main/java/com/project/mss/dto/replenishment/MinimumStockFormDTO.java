package com.project.mss.dto.replenishment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Full minimum levels of a REF (PUT). idealTotal must be greater than or equal to hospitalIdeal. */
public record MinimumStockFormDTO(
        @NotNull @PositiveOrZero Integer hospitalIdeal,
        @NotNull @PositiveOrZero Integer idealTotal
) { }
