package com.project.mss.model.enums;

/**
 * What happened to units that were dispatched and were not in the box. One of these has to be chosen to
 * settle the pending issue, because until then their balance sits on the road and counts as stock.
 */
public enum ShipmentOutcome {
    /** It turned up: the material is at the hospital after all, and enters its stock. */
    ARRIVED,
    /** It never actually left: the balance goes back to the place the shipment came from. */
    RETURNED,
    /** Gone for good. Leaves the stock, and the record is what a claim to the carrier is built on. */
    LOST
}
