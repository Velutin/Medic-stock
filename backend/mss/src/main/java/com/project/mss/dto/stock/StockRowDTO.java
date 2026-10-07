package com.project.mss.dto.stock;

import java.time.LocalDate;

/** One row of the hospital stock view, in spreadsheet layout. */
public record StockRowDTO(
        Long materialId,
        String ref,
        String description,
        java.util.List<com.project.mss.model.enums.ProductLine> productLines,
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
