package com.project.mss.dto.replenishment;

import jakarta.validation.constraints.PositiveOrZero;

/** Partial change of the minimum levels of a REF (PATCH). Empty fields keep their current value. */
public record MinimumStockPatchDTO(
        @PositiveOrZero Integer hospitalIdeal,
        @PositiveOrZero Integer idealTotal
) { }
