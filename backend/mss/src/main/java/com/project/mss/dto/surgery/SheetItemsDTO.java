package com.project.mss.dto.surgery;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Labels read from the consumption sheet (each label = 1 item). Per label:
 *  - code: full GS1 text of the 2D code (QR/Data Matrix) when the label has one (GTIN, lot and expiry date);
 *  - lot: lot number (1D barcode of the lot, or typed);
 *  - gtin: GTIN barcode read beside the lot (optional; only used to tell lots apart);
 *  - ref: REF, when known;
 *  - lotId: lot chosen on screen among the hospital lots (when the lot could not be read or was ambiguous).
 */
public record SheetItemsDTO(@NotEmpty @Valid List<LabelDTO> labels) {
    public record LabelDTO(String lot, String ref, String gtin, String code, Long lotId) { }
}
