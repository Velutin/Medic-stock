package com.project.mss.dto.stock;

import java.util.List;

import com.project.mss.model.enums.ProductLine;

/**
 * Surgical tech's stock line: quantity inside the hospital, valid lots only, without lot details.
 * Lists the REFs with an ideal greater than zero in the hospital (quantity 0 when there is no balance)
 * plus any REF with balance inside the hospital. Grouped on screen by section (sectionOrder, then name).
 *
 * inTransit is what has been dispatched to this hospital and not yet confirmed. It is a number of its own
 * and is never added to quantity: the tech decides by what is physically on the shelf, and a sum of the two
 * would answer the wrong question. It comes back as zero for the read-only profile, which does not see
 * internal movement.
 */
public record StockSummaryDTO(Long materialId, String ref, String component, String description, String size,
                              String color, List<ProductLine> productLines, int quantity, int inTransit,
                              Long sectionId, String section, Integer sectionOrder) { }
