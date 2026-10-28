package com.project.mss.dto.invoice;

/**
 * What came of reading one XML.
 *
 * Reading a mailbox produces outcomes that are not errors: the same invoice sent twice, and invoices that are
 * not material coming to us. They answer with an outcome instead of an exception so the reader can record
 * them and carry on to the next message.
 */
public record InvoiceImportResultDTO(Outcome outcome, String detail, InvoiceDTO invoice) {

    public enum Outcome {
        /** Invoice registered with its items. */
        IMPORTED,
        /** Already registered, with items; nothing changed. */
        DUPLICATE,
        /** Filed: a return, an unknown emitter or a CFOP outside the shipment list. */
        NOT_SHIPMENT
    }

    public static InvoiceImportResultDTO imported(InvoiceDTO invoice) {
        return new InvoiceImportResultDTO(Outcome.IMPORTED, null, invoice);
    }

    public static InvoiceImportResultDTO duplicate(InvoiceDTO invoice) {
        return new InvoiceImportResultDTO(Outcome.DUPLICATE, "Invoice already registered", invoice);
    }

    public static InvoiceImportResultDTO notShipment(InvoiceDTO invoice, String reason) {
        return new InvoiceImportResultDTO(Outcome.NOT_SHIPMENT, reason, invoice);
    }
}
