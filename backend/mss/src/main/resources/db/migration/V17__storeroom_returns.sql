-- V17 - Material taken out of a hospital and back into the storeroom.
--
-- Until now material only moved one way: it entered the storeroom and reached the hospital through a
-- delivery, and the only ways out of a hospital were a surgery, a loan to another hospital or a return to
-- the supplier. Material sitting inside a hospital that simply has to come back to the storeroom - too much
-- of a size, a line the hospital stopped using, a lot about to expire that is worth more somewhere else -
-- had no way out, so it stayed there.
--
-- It is its own document and not a Delivery with a direction, because a delivery is signed by whoever
-- receives the material at the hospital, and here the hospital is the one handing it over. Keeping them
-- apart also keeps the Deliveries tab what it says it is.
--
-- Expired lots CAN be taken out, which is the opposite of the delivery rule. Clearing expired material out
-- of a hospital is the main reason this exists, and refusing it would leave that material stuck inside.

-- Material leaves (hospital, HOSPITAL) and enters (storeroom owner, STOREROOM). The storeroom owner is the
-- hospital itself or, for a hospital supplied by a distribution center, the center: a covered hospital has
-- no storeroom of its own, so its material can only go back to the center that supplies it.
CREATE TABLE storeroom_return (
    id                   BIGSERIAL PRIMARY KEY,
    hospital_id          BIGINT      NOT NULL REFERENCES hospital (id),
    storeroom_hospital_id BIGINT     NOT NULL REFERENCES hospital (id),
    reason               TEXT        NOT NULL,
    notes                TEXT,
    created_by           BIGINT      REFERENCES users (id),
    created_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_storeroom_return_hospital ON storeroom_return (hospital_id, created_at DESC);

CREATE TABLE storeroom_return_item (
    id        BIGSERIAL PRIMARY KEY,
    return_id BIGINT  NOT NULL REFERENCES storeroom_return (id) ON DELETE CASCADE,
    lot_id    BIGINT  NOT NULL REFERENCES lot (id),
    quantity  INTEGER NOT NULL CHECK (quantity > 0)
);
CREATE INDEX idx_storeroom_return_item_return ON storeroom_return_item (return_id);

-- The document each movement belongs to, like surgery_id, loan_id and delivery_id already do. Without it the
-- only trace linking the rows of one withdrawal would be the text in notes.
ALTER TABLE stock_movement ADD COLUMN storeroom_return_id BIGINT
    REFERENCES storeroom_return (id) ON DELETE SET NULL;

-- The movement ledger gains the new type. Reusing REPLENISHMENT would have made the movements report count
-- the two directions together, and telling them apart would depend on reading the origin and destination
-- columns of every row.
ALTER TABLE stock_movement DROP CONSTRAINT stock_movement_type_check;
ALTER TABLE stock_movement ADD CONSTRAINT stock_movement_type_check
    CHECK (type IN ('ENTRY', 'ENTRY_CORRECTION', 'INVENTORY_ADJUSTMENT', 'REPLENISHMENT',
                    'SURGERY_WITHDRAWAL', 'SURGERY_REVERSAL', 'LOAN', 'SUPPLIER_RETURN',
                    'LOT_CORRECTION', 'STOREROOM_RETURN'));
