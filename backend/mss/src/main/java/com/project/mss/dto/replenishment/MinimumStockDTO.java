package com.project.mss.dto.replenishment;

import com.project.mss.model.entity.MinimumStock;

/** Minimum levels of a REF for a hospital: hospitalIdeal (inside the hospital) and idealTotal (hospital + storeroom). */
public record MinimumStockDTO(Long materialId, String ref, String component, String description,
                              int hospitalIdeal, int idealTotal) {
    public static MinimumStockDTO of(MinimumStock m) {
        return new MinimumStockDTO(m.getMaterial().getId(), m.getMaterial().getRef(), m.getMaterial().getComponent(),
                m.getMaterial().getDescription(), m.getHospitalIdeal(), m.getIdealTotal());
    }
}
