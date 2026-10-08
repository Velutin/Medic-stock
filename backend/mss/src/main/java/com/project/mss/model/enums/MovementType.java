package com.project.mss.model.enums;

public enum MovementType {
    ENTRY,
    /** Correction of a stock entry already recorded (signed quantity, like INVENTORY_ADJUSTMENT). */
    ENTRY_CORRECTION,
    INVENTORY_ADJUSTMENT,
    REPLENISHMENT,
    SURGERY_WITHDRAWAL,
    SURGERY_REVERSAL,
    LOAN,
    /** Material returned to the supplier: leaves the stock of the source hospital (or storeroom). */
    SUPPLIER_RETURN,
    /**
     * Lot number or expiry date corrected by an administrator (typing error at the entry). Nothing moves:
     * the row carries quantity 0 and no hospital, and notes describe what the lot was and what it became.
     */
    LOT_CORRECTION
}
