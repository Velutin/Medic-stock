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
    LOT_CORRECTION,
    /**
     * Material taken out of a hospital and back into the storeroom: the opposite way of REPLENISHMENT.
     * Its own type so the movements report counts the two directions apart instead of making them one
     * "transfer" number that only the origin and destination columns could separate.
     */
    STOREROOM_RETURN,
    /**
     * Second leg of a shipment: it reached the hospital and left the road. The first leg keeps the type it
     * always had (REPLENISHMENT or LOAN), so the movements report still counts one transfer per transfer
     * instead of two.
     */
    SHIPMENT_ARRIVAL,
    /** A shipment went back where it came from: called off, or it turned out never to have left. */
    SHIPMENT_RETURNED,
    /** A shipment, or part of it, is gone. Leaves the stock and enters nowhere, like a supplier return. */
    SHIPMENT_LOST
}
