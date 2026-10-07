-- V10 - Return to the supplier (Baumer): recorded with the loans, but the material leaves every stock.
-- A return has no destination hospital and no notification status; the reason is required.

ALTER TABLE loan ADD COLUMN type VARCHAR(10) NOT NULL DEFAULT 'LOAN' CHECK (type IN ('LOAN', 'RETURN'));
ALTER TABLE loan ADD COLUMN return_reason TEXT;
ALTER TABLE loan ALTER COLUMN destination_hospital_id DROP NOT NULL;
ALTER TABLE loan ALTER COLUMN status DROP NOT NULL;
ALTER TABLE loan ADD CONSTRAINT loan_type_fields CHECK (
    (type = 'LOAN' AND destination_hospital_id IS NOT NULL AND status IS NOT NULL)
    OR (type = 'RETURN' AND destination_hospital_id IS NULL AND status IS NULL AND return_reason IS NOT NULL));

ALTER TABLE stock_movement DROP CONSTRAINT stock_movement_type_check;
ALTER TABLE stock_movement ADD CONSTRAINT stock_movement_type_check
    CHECK (type IN ('ENTRY', 'ENTRY_CORRECTION', 'INVENTORY_ADJUSTMENT', 'REPLENISHMENT',
                    'SURGERY_WITHDRAWAL', 'SURGERY_REVERSAL', 'LOAN', 'SUPPLIER_RETURN'));
