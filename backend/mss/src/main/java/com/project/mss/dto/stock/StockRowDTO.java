package com.project.mss.dto.stock;

import java.time.LocalDate;

/** One row of the hospital stock view, in spreadsheet layout. */
public record StockRowDTO(
        Long materialId,
        String ref,
        String description,
        String component,
        String size,
        String color,
        Long lotId,
        String lot,
        LocalDate expiryDate,
        boolean expired,
        int hospitalQuantity,
        int storeroomQuantity
) { }
