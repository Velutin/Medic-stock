package com.project.mss.dto.replenishment;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Changes several REFs of the hospital minimum list at once (PATCH on the collection).
 * Listed REFs are created or replaced; a REF with both levels 0 leaves the list; REFs not listed are kept.
 */
public record MinimumStockBatchDTO(@NotEmpty @Valid List<Item> items) {
    public record Item(@NotNull Long materialId,
                       @NotNull @PositiveOrZero Integer hospitalIdeal,
                       @NotNull @PositiveOrZero Integer idealTotal) { }
}
