package com.project.mss.dto.stock;

import java.util.List;

import com.project.mss.model.enums.ProductLine;

/** Surgical tech's stock line: quantity inside the hospital, valid lots only, without lot details. */
public record StockSummaryDTO(Long materialId, String ref, String component, String description, String size,
                              String color, List<ProductLine> productLines, int quantity) { }
