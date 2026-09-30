package com.project.mss.dto.stock;

import java.time.LocalDate;

import com.project.mss.model.enums.Location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Manual stock entry (usually in the STOREROOM, assigned to the hospital). */
public record StockEntryDTO(
        @NotNull Long hospitalId,
        @NotNull Location location,
        @NotBlank String ref,
        @NotBlank String lot,
        @NotNull LocalDate expiryDate,
        @NotNull @Positive Integer quantity
) { }
