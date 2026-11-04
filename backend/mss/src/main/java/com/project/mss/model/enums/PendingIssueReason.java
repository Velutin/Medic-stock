package com.project.mss.model.enums;

public enum PendingIssueReason {
    LOT_NOT_FOUND,
    AMBIGUOUS_LOT,
    NO_HOSPITAL_BALANCE,
    EXPIRED_LOT,
    /**
     * The lot entered the stock with no open invoice owing it, while the invoice was required at the entry.
     *
     * The entry is not refused: the material is in the box and the count is right. What is missing is the
     * paperwork, and that is settled here, by the administrator, instead of at the bench.
     */
    WITHOUT_INVOICE,
    /**
     * Units that were dispatched and were not in the box when it was opened.
     *
     * Their balance stays on the road, not because anyone expects a second delivery - a shipment is one box -
     * but because what happened is not known yet. It is settled by hand: the material turned up, it never
     * actually left, or it is gone for good and the carrier gets a claim.
     */
    NOT_DELIVERED
}
