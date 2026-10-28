package com.project.mss.model.enums;

/**
 * What happened to a message read from the invoice mailbox. Every message gets one, including the ones the
 * reader does not understand: a message read and dropped in silence is invisible, and invisible is the
 * problem this whole feature exists to remove.
 */
public enum IncomingEmailStatus {

    /** At least one invoice was created from its attachments. */
    IMPORTED,

    /** Every invoice it carried was already registered (the same XML sent again). */
    DUPLICATE,

    /** Read, but not material coming to us: a return, or a CFOP outside the shipment list. */
    NOT_SHIPMENT,

    /** A tracking e-mail: no attachment, but it says which invoices travelled under which code. */
    TRACKING,

    /** No NF-e XML inside, and nothing else the reader knows how to use. */
    UNRECOGNISED,

    /** Something broke while reading it; the detail says what. */
    FAILED
}
