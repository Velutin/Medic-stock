-- V12 - Correcting a lot.
-- The administrator fixes a lot number or an expiry date typed wrong at the entry, from the stock by lot.
-- The correction is written to the movement ledger, so the type list gains LOT_CORRECTION. The row carries
-- quantity 0 and no hospital: nothing moved, what changed is the identity of the lot, described in notes.

ALTER TABLE stock_movement DROP CONSTRAINT stock_movement_type_check;
ALTER TABLE stock_movement ADD CONSTRAINT stock_movement_type_check
    CHECK (type IN ('ENTRY', 'ENTRY_CORRECTION', 'INVENTORY_ADJUSTMENT', 'REPLENISHMENT',
                    'SURGERY_WITHDRAWAL', 'SURGERY_REVERSAL', 'LOAN', 'SUPPLIER_RETURN',
                    'LOT_CORRECTION'));
