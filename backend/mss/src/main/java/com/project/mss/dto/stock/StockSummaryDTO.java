package com.project.mss.dto.stock;

import java.util.List;

import com.project.mss.model.enums.ProductLine;

/**
 * Surgical tech's stock line: quantity inside the hospital, valid lots only, without lot details.
 * Lists the REFs with an ideal greater than zero in the hospital (quantity 0 when there is no balance)
 * plus any REF with balance inside the hospital. Grouped on screen by section (sectionOrder, then name).
 */
public record StockSummaryDTO(Long materialId, String ref, String component, String description, String size,
                              String color, List<ProductLine> productLines, int quantity,
                              Long sectionId, String section, Integer sectionOrder) { }
