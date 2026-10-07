package com.project.mss.dto.replenishment;

/**
 * Minimum levels and valid balances of a REF in a hospital (minimums editing screen).
 * hospitalIdeal/idealTotal are 0 when the REF has no minimum levels in the hospital.
 * hospitalBalance: inside the hospital (for a distribution center, inside the hospitals it supplies).
 * storeroomBalance: in the storeroom that supplies the hospital (its own or its distribution center's).
 */
public record StockLevelDTO(Long materialId, String ref, String component, String description, String size,
                            String color, Long sectionId, String section, Integer sectionOrder,
                            int hospitalIdeal, int idealTotal, int hospitalBalance, int storeroomBalance) { }
