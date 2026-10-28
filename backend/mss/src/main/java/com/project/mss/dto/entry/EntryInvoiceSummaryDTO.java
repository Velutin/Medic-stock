package com.project.mss.dto.entry;

import java.util.List;

/**
 * What an entry settled against the invoices, answered right after it is registered.
 *
 * This is the screen that closes the audit hole: having read the box, it says invoice by invoice what is
 * still missing, instead of leaving the difference to be discovered months later in a count.
 */
public record EntryInvoiceSummaryDTO(List<InvoiceLine> invoices,
                                     List<UnlinkedLine> unlinked,
                                     List<InstrumentLine> instrumentsAwaiting,
                                     int pendingIssues) {

    /** An invoice this entry touched, with what it still owes. */
    public record InvoiceLine(Long id, String number, String emitter, String status, String trackingCode,
                              int receivedUnits, int announcedUnits, List<MissingLine> missing) { }

    public record MissingLine(Long invoiceItemId, String productCode, String description, int missing,
                              String category) { }

    /** Lots received that no open invoice was owing: material arrived without a note behind it. */
    public record UnlinkedLine(String ref, String lot, int quantity) { }

    /**
     * Instruments still owed by invoices that travelled under the same tracking code. The entry asks about
     * them because the supplier said they came in the same box - never because the dates are close.
     */
    public record InstrumentLine(Long invoiceItemId, String invoiceNumber, String productCode,
                                 String description, int missing, String trackingCode) { }

    /**
     * Whether there is anything worth stopping the person for after the entry.
     *
     * Lots with no invoice are deliberately NOT a reason on their own: while the invoices are still catching
     * up, every lot is one of those, and a dialog that opens after every entry saying what you already know
     * is a dialog people learn to dismiss without reading. They are reported next to the invoices that were
     * found, and as pending issues once the invoice is required.
     */
    public boolean isEmpty() {
        return invoices.isEmpty() && instrumentsAwaiting.isEmpty() && pendingIssues == 0;
    }
}
