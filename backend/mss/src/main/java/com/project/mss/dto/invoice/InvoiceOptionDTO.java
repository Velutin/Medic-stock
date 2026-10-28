package com.project.mss.dto.invoice;

import java.time.LocalDate;

/**
 * An open invoice that is owing a given lot, offered when a pending issue about a missing invoice is opened.
 *
 * It exists so the administrator chooses from what the system knows instead of typing a number from memory.
 * missing is what that invoice still has not received of this lot's item - an invoice owing one unit and an
 * invoice owing twelve are very different answers to "is this the one".
 */
public record InvoiceOptionDTO(Long id, String number, String emitter, LocalDate issuedAt, String status,
                               String productCode, String description, int missing, boolean declared) { }
