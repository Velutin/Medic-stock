package com.project.mss.dto.entry;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Creates a stock entry or replaces its content (correction). The material always enters the storeroom,
 * assigned to hospitalId (a regular hospital or a distribution center such as SESAB).
 * Lots are identified by material + number + expiry date and created when new; the same lot repeated is summed.
 * monthOnly: the expiry date was typed as MM/AAAA (sent as day 1); an existing lot with the same number and the
 * same expiry month is reused instead of creating another one.
 * changeRef: a lot number belongs to only one REF. When the number is already registered with another REF, the
 * user confirms (true) that those lots become this item's REF; refused for lots already used in a surgery.
 */
public record EntryFormDTO(
        @NotNull(message = "Destination is required") Long hospitalId,
        @NotNull(message = "Entry date is required")
        @PastOrPresent(message = "The entry date cannot be in the future") LocalDate entryDate,
        @Size(max = 1000) String notes,
        @NotEmpty(message = "Add at least one item") @Valid List<Item> items
) {
    public record Item(
            @NotNull(message = "Material is required") Long materialId,
            @NotBlank(message = "Lot is required") @Size(max = 80) String lot,
            @NotNull(message = "Expiry date is required") LocalDate expiryDate,
            @NotNull @Positive(message = "Quantity must be greater than zero") Integer quantity,
            Boolean monthOnly,
            Boolean changeRef
    ) { }
}
