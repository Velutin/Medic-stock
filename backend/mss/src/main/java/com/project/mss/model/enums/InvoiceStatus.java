package com.project.mss.model.enums;

/**
 * Where an invoice stands between leaving the supplier and being received.
 *
 * PARTIAL is an alert like OPEN, not a halfway success: what is missing is exactly what has to be charged
 * to the supplier. Only the administrator moves an invoice to CLOSED, with a reason.
 */
public enum InvoiceStatus {

    /** Named by a tracking e-mail, XML not read yet: the items are unknown. */
    AWAITING_XML,

    /** Items announced, nothing received yet. */
    OPEN,

    /** Part of the items received; the rest is still owed. */
    PARTIAL,

    /** Every item received. */
    RECEIVED,

    /** Closed by the administrator with a reason, without having been fully received. */
    CLOSED,

    /** Read and filed: not material coming to us (a return, an unknown CFOP). */
    NOT_SHIPMENT;

    /** Invoices that still wait for material and therefore can be charged to the supplier. */
    public boolean isOpen() {
        return this == AWAITING_XML || this == OPEN || this == PARTIAL;
    }
}
