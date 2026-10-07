package com.project.mss.dto.surgery;

import java.time.LocalDate;

/**
 * Lot inside the hospital suggested to resolve a pending issue, as the surgery screen offers it: lots of the material
 * identified by the code read (GTIN, REF or the lot number itself). sameLot: the lot number is the one read.
 */
public record PendingLotOptionDTO(Long lotId, String ref, String material, String lot, LocalDate expiryDate,
                                  int available, boolean sameLot) { }
