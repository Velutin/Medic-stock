package com.project.mss.model.enums;

/**
 * Where a shipment is in its journey. A shipment is always one box: there is no second batch, so what is
 * missing when the box is opened is a loss, not a late delivery.
 */
public enum ShipmentStatus {
    /** Left the origin, has not been confirmed at the destination. The balance sits in IN_TRANSIT. */
    DISPATCHED,
    /**
     * Confirmed at the destination. What was received entered the hospital; anything missing left a pending
     * issue behind and its balance stays on the road until that is settled.
     */
    ARRIVED,
    /** Called off before anyone confirmed anything. The balance went back to where it came from. */
    CANCELLED,
    /**
     * The box never arrived. The balance left the road and left the stock, with a movement per lot.
     *
     * It exists as an ending of its own because it is the likely one when the post goes wrong, and because
     * the alternative was recording it as "zero arrived" line by line and then settling every pending issue
     * it created - eighty entries for one event on a box of forty items.
     */
    LOST
}
