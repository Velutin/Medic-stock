package com.project.mss.dto.replenishment;

/**
 * One row of a minimums spreadsheet read for review (nothing is saved).
 * current*: levels the REF has today in the hospital (0 when it is not in the list).
 * error: why the row cannot be applied (unknown REF, missing or invalid levels).
 */
public record MinimumPreviewRowDTO(int row, String ref, Long materialId, String description,
                                   Integer hospitalIdeal, Integer idealTotal,
                                   int currentHospitalIdeal, int currentIdealTotal, String error) { }
