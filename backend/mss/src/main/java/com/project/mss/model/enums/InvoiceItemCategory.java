package com.project.mss.model.enums;

/**
 * How an item of an invoice is received.
 *
 * The catalog decides: a GTIN registered in Materials is an implant, which arrives with a readable label and
 * is received by scanning it. Anything else is an instrument (a drill, a trial), which arrives loose and is
 * received by confirmation. Registering the material at the entry turns it into an implant from then on.
 *
 * The category never changes what is charged: an invoice is alerted by date, whatever its items are.
 */
public enum InvoiceItemCategory {

    IMPLANT,
    INSTRUMENT
}
