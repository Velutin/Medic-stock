package com.project.mss.dto.entry;

import java.time.LocalDate;

/**
 * A lot an open invoice is still owing, as the entry screen uses it.
 *
 * It is what makes a one-step entry possible: reading the lot barcode is enough, because the REF and the
 * expiry date were already declared by the manufacturer in the invoice. Typing neither of them removes two
 * chances of typing them wrong.
 *
 * monthOnly says the invoice gave the first day of the month rather than a real day - which is how the
 * older labels are. The entry treats that date as provisional, exactly as it treats a date typed as
 * MM/AAAA: a full date read later from a QR code replaces it on the same lot instead of creating another.
 */
public record InvoiceLotDTO(Long materialId, String ref, String description, String component, String size,
                            String color, String lot, LocalDate expiryDate, boolean monthOnly,
                            Long invoiceId, String invoiceNumber, String emitter, int missing) { }
