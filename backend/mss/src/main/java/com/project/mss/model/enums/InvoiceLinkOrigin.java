package com.project.mss.model.enums;

/** How a received unit came to be written against an invoice item. */
public enum InvoiceLinkOrigin {

    /** Matched by the lot number when the entry was registered. */
    AUTO_LOT,

    /** Chosen or corrected by the administrator. */
    MANUAL,

    /**
     * Instrument confirmed as received. It has no label to scan, so nothing entered the stock through a lot:
     * what is recorded is that the administrator saw it in the box. Kept apart from AUTO_LOT on purpose -
     * for an audit, what was checked by a scanner and what was checked by eye are not the same evidence.
     */
    CONFIRMED
}
