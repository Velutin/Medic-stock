package com.project.mss.dto.stock;

import java.time.LocalDate;

/**
 * One lot at one hospital, with the quantity inside the hospital, in the storeroom assigned to it and on
 * its way there. Used by the administrator's stock tab ("by lot"), for one hospital or every hospital.
 *
 * The three numbers are kept apart and never summed here. inTransitQuantity comes back as zero for the
 * read-only profile, whose query only ever looks inside the hospital.
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
        long storeroomQuantity,
        long inTransitQuantity
) {
    /** Used by the JPQL query of one single location; computes the expired flag. Nothing is on the road. */
    public LotStockDTO(Long lotId, Long materialId, String ref, String component, String description, String size,
                       String color, String lot, LocalDate expiryDate, Long hospitalId, String hospital,
                       Long hospitalQuantity, Long storeroomQuantity) {
        this(lotId, materialId, ref, component, description, size, color, lot, expiryDate, hospitalId, hospital,
                hospitalQuantity, storeroomQuantity, null);
    }

    /** Used by the JPQL query of the three locations; computes the expired flag. */
    public LotStockDTO(Long lotId, Long materialId, String ref, String component, String description, String size,
                       String color, String lot, LocalDate expiryDate, Long hospitalId, String hospital,
                       Long hospitalQuantity, Long storeroomQuantity, Long inTransitQuantity) {
        this(lotId, materialId, ref, component, description, size, color, lot, expiryDate,
                expiryDate.isBefore(LocalDate.now()), hospitalId, hospital,
                hospitalQuantity == null ? 0 : hospitalQuantity, storeroomQuantity == null ? 0 : storeroomQuantity,
                inTransitQuantity == null ? 0 : inTransitQuantity);
    }
}
