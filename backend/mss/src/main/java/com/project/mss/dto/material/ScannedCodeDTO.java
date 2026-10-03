package com.project.mss.dto.material;

import java.time.LocalDate;

/**
 * What a scanned or typed code identifies, without requiring the lot to be registered
 * (used by stock entries, where the lot is usually new, and by deliveries).
 * gtin: AI (01) or a bare GTIN; lot: AI (10) or "LOTE:" label; expiryDate: AI (17);
 * material: the registered material found by GTIN or REF, or null when unknown.
 */
public record ScannedCodeDTO(String code, String gtin, String ref, String lot, LocalDate expiryDate,
                             MaterialDTO material) { }
