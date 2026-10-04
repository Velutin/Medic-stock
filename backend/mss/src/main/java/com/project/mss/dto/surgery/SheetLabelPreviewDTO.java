package com.project.mss.dto.surgery;

import java.time.LocalDate;
import java.util.List;

/**
 * What a sheet label will become when recorded, without recording it.
 * status: OK (recorded as an item) | NOT_FOUND | AMBIGUOUS | EXPIRED | NO_BALANCE (become pending issues)
 *       | NO_LOT (the lot could not be read: choose one of the options or type it).
 * options: lots of the identified material inside the hospital (valid, with balance), earliest expiry first.
 */
public record SheetLabelPreviewDTO(int index, String status, Long lotId, Long materialId, String ref,
                                   String component, String description, String size, String lot,
                                   LocalDate expiryDate, List<LotOption> options) {

    public record LotOption(Long lotId, String lot, LocalDate expiryDate, int available) { }
}
