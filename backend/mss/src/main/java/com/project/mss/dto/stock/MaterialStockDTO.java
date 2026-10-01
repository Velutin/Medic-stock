package com.project.mss.dto.stock;

import java.time.LocalDate;
import java.util.List;

/**
 * Stock of one material (REF) across the hospitals visible to the user.
 * Totals only count non-expired lots; expired lots are listed (when requested) but flagged.
 */
public record MaterialStockDTO(
        Long materialId,
        String ref,
        String component,
        String description,
        String size,
        String color,
        int totalHospitalQuantity,
        int totalStoreroomQuantity,
        int totalAvailable,
        List<HospitalStock> hospitals
) {

    public record HospitalStock(
            Long hospitalId,
            String hospitalName,
            int hospitalQuantity,
            int storeroomQuantity,
            int available,
            List<LotStock> lots
    ) { }

    public record LotStock(
            Long lotId,
            String lot,
            LocalDate expiryDate,
            boolean expired,
            int hospitalQuantity,
            int storeroomQuantity
    ) { }
}