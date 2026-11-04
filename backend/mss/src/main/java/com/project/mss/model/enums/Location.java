package com.project.mss.model.enums;

/**
 * Where a balance sits. A lot can have units in more than one of these at the same time, in the same
 * hospital: the UNIQUE of the stock table is (lot, hospital, location).
 */
public enum Location {
    /** In a storeroom, assigned to a hospital. A distribution center only ever has this one. */
    STOREROOM,
    /** Inside the hospital, available for a surgery. */
    HOSPITAL,
    /**
     * Dispatched and not yet received: it left the origin and has not entered the hospital. Belongs to the
     * DESTINATION hospital, which is the one it is travelling to.
     *
     * It exists because the transfer used to debit the storeroom and credit the hospital in one transaction,
     * while the box takes a day or a week to get there - and a box that never arrives left the system
     * claiming the material was in the hospital for good.
     *
     * Not available for a surgery, and never summed into what the hospital has.
     */
    IN_TRANSIT
}
