package com.project.mss.dto.stock;

import java.time.LocalDate;

/**
 * One lot at one hospital, with the quantity inside the hospital and in the storeroom assigned to it.
 * Used by the administrator's stock tab ("by lot"), for one hospital or every hospital.
 */
public record LotStockDTO(
        Long lotId,
        Long materialId,
        String ref,
        String component,
        String description,
        String size,
        String color,
        String lot,
        LocalDate expiryDate,
        boolean expired,
        Long hospitalId,
        String hospital,
        long hospitalQuantity,
        long storeroomQuantity
) {
    /** Used by the JPQL query; computes the expired flag. */
    public LotStockDTO(Long lotId, Long materialId, String ref, String component, String description, String size,
                       String color, String lot, LocalDate expiryDate, Long hospitalId, String hospital,
                       Long hospitalQuantity, Long storeroomQuantity) {
        this(lotId, materialId, ref, component, description, size, color, lot, expiryDate,
                expiryDate.isBefore(LocalDate.now()), hospitalId, hospital,
                hospitalQuantity == null ? 0 : hospitalQuantity, storeroomQuantity == null ? 0 : storeroomQuantity);
    }
}
