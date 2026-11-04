-- V19 - A shipment is dispatched, travels, and arrives.
--
-- The V18 opened the third place; this one makes the material actually go through it. Until now a transfer
-- and a loan were a single instant: the balance left the origin and entered the destination in the same
-- transaction, whatever the box was doing. From here a shipment has a life - DISPATCHED while it travels,
-- ARRIVED when someone confirms it, CANCELLED when it never left.
--
-- A shipment is always one box: there is no second batch. What is missing when the box is opened is not a
-- late delivery, it is a loss, and it becomes a pending issue instead of waiting.

-- --------------------------------------------------------------------------- transfers
ALTER TABLE delivery ADD COLUMN status VARCHAR(12) NOT NULL DEFAULT 'ARRIVED'
    CHECK (status IN ('DISPATCHED', 'ARRIVED', 'CANCELLED'));
ALTER TABLE delivery ADD COLUMN arrived_on DATE;
ALTER TABLE delivery ADD COLUMN arrived_by BIGINT REFERENCES users (id);
ALTER TABLE delivery_item ADD COLUMN arrived_quantity INTEGER;

-- --------------------------------------------------------------------------- loans
-- Only a loan between hospitals travels. A return to the supplier (type RETURN) leaves the stock when it is
-- dispatched and is confirmed by Baumer outside the system, so it stays ARRIVED from the start.
ALTER TABLE loan ADD COLUMN status VARCHAR(12) NOT NULL DEFAULT 'ARRIVED'
    CHECK (status IN ('DISPATCHED', 'ARRIVED', 'CANCELLED'));
ALTER TABLE loan ADD COLUMN arrived_on DATE;
ALTER TABLE loan ADD COLUMN arrived_by BIGINT REFERENCES users (id);
ALTER TABLE loan_item ADD COLUMN arrived_quantity INTEGER;

-- Everything already recorded arrived: the balance is in the hospital and has been for a while. The DEFAULT
-- above did that for the existing rows; arrived_on repeats the day it was created, which is the day the
-- system considered the material delivered.
UPDATE delivery SET arrived_on = created_at::date WHERE arrived_on IS NULL;
UPDATE loan     SET arrived_on = created_at::date WHERE arrived_on IS NULL;
UPDATE delivery_item i SET arrived_quantity = i.quantity WHERE arrived_quantity IS NULL;
UPDATE loan_item     i SET arrived_quantity = i.quantity WHERE arrived_quantity IS NULL;

-- The default only existed to fill the old rows. A shipment created from now on says what it is.
ALTER TABLE delivery ALTER COLUMN status DROP DEFAULT;
ALTER TABLE loan     ALTER COLUMN status DROP DEFAULT;

CREATE INDEX idx_delivery_status ON delivery (status, created_at DESC);
CREATE INDEX idx_loan_status ON loan (status, created_at DESC);

-- --------------------------------------------------------------------------- what did not arrive
-- The pending issue table already holds two origins - a surgery withdrawal and a stock entry - each with a
-- nullable key of its own and the others left null. The shipment is the third, by the same pattern.
ALTER TABLE pending_issue ADD COLUMN delivery_item_id BIGINT REFERENCES delivery_item (id) ON DELETE SET NULL;
ALTER TABLE pending_issue ADD COLUMN loan_item_id BIGINT REFERENCES loan_item (id) ON DELETE SET NULL;

ALTER TABLE pending_issue DROP CONSTRAINT IF EXISTS pending_issue_reason_check;
ALTER TABLE pending_issue ADD CONSTRAINT pending_issue_reason_check
    CHECK (reason IN ('LOT_NOT_FOUND', 'AMBIGUOUS_LOT', 'NO_HOSPITAL_BALANCE', 'EXPIRED_LOT',
                      'WITHOUT_INVOICE', 'NOT_DELIVERED'));

CREATE INDEX idx_pending_issue_delivery_item ON pending_issue (delivery_item_id);
CREATE INDEX idx_pending_issue_loan_item ON pending_issue (loan_item_id);

-- --------------------------------------------------------------------------- the two legs of a journey
-- Every balance change writes a movement - that is the invariant of StockService - and a shipment now moves
-- the balance twice: out of the origin when it is dispatched, and into the hospital when it arrives.
--
-- The dispatch keeps the type it always had (REPLENISHMENT for a transfer, LOAN for a loan), so the
-- movements report still counts one transfer per transfer. The second leg gets its own type instead of
-- repeating the first, which would have doubled every count in that report.
--
-- Three ways out of the road, because they are three different stories and the history has to say which:
--   SHIPMENT_ARRIVAL  - it got there, and entered the hospital
--   SHIPMENT_RETURNED - it went back to where it came from (cancelled, or it never actually left)
--   SHIPMENT_LOST     - it is gone: leaves the stock and enters nowhere, like a return to the supplier
ALTER TABLE stock_movement DROP CONSTRAINT stock_movement_type_check;
ALTER TABLE stock_movement ADD CONSTRAINT stock_movement_type_check
    CHECK (type IN ('ENTRY', 'ENTRY_CORRECTION', 'INVENTORY_ADJUSTMENT', 'REPLENISHMENT',
                    'SURGERY_WITHDRAWAL', 'SURGERY_REVERSAL', 'LOAN', 'SUPPLIER_RETURN',
                    'LOT_CORRECTION', 'STOREROOM_RETURN',
                    'SHIPMENT_ARRIVAL', 'SHIPMENT_RETURNED', 'SHIPMENT_LOST'));
