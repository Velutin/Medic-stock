package com.project.mss.model.enums;

/**
 * REPLACE: the spreadsheet is the hospital's complete table; values of REFs not in it are removed.
 * UPDATE: only the REFs in the spreadsheet are created or changed; the others stay as they are.
 */
public enum PriceImportMode {
    REPLACE,
    UPDATE
}
