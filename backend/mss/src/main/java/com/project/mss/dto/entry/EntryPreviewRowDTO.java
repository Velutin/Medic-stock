package com.project.mss.dto.entry;

import java.time.LocalDate;

/**
 * One spreadsheet row read for review before the entry is recorded (nothing is saved).
 * materialId is null when the REF is not in the catalog yet (it can be registered on screen);
 * error is filled when the row cannot be used as it is (missing lot, invalid date or quantity, expired lot).
 */
public record EntryPreviewRowDTO(int row, String ref, Long materialId, String description, String gtin,
                                 String lot, LocalDate expiryDate, Integer quantity, String error) { }
