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
    SUPPLIER_RETURN
}
