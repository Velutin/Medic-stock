package com.project.mss.dto.replenishment;

import java.util.List;

/**
 * Status of a REF against the hospital minimums. Expired lots are not counted.
 * replenishFromStoreroom: missing quantity to reach the hospital ideal that can come from the storeroom.
 * orderFromSupplier: missing quantity to reach the ideal total (hospital + storeroom).
 * storeroomHospitalId: whose storeroom the lots come from (the hospital itself or its distribution center).
 * For hospitals supplied by a distribution center, orderFromSupplier is always 0: orders are placed by the center.
 */
public record ReplenishmentSuggestionDTO(
        Long materialId,
        String ref,
        String description,
        int hospitalIdeal,
        int idealTotal,
        int hospitalBalance,
        int storeroomBalance,
        int replenishFromStoreroom,
        int orderFromSupplier,
        Long storeroomHospitalId,
        List<SuggestedLot> storeroomLots
) {
    /** Storeroom lots suggested by expiry date (first expired, first out). */
    public record SuggestedLot(Long lotId, String lot, java.time.LocalDate expiryDate, int quantity) { }
}
