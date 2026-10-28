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
    WITHOUT_INVOICE
}
