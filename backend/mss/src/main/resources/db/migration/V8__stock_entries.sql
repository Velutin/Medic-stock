-- V8 - Stock entries: material received from the supplier into the storeroom, grouped per receipt
-- so the whole receipt can be reviewed and corrected later (wrong REF, lot, expiry date or quantity).

CREATE TABLE stock_entry (
    id          BIGSERIAL PRIMARY KEY,
    -- Hospital (or distribution center) the material is assigned to; it always enters the storeroom
    hospital_id BIGINT    NOT NULL REFERENCES hospital (id),
    entry_date  DATE      NOT NULL,
    notes       TEXT,
    created_by  BIGINT REFERENCES users (id),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT REFERENCES users (id),
    updated_at  TIMESTAMP
);
CREATE INDEX idx_stock_entry_date ON stock_entry (entry_date DESC, id DESC);
CREATE INDEX idx_stock_entry_hospital ON stock_entry (hospital_id);

-- One line per lot (same lot typed twice in a receipt is summed)
CREATE TABLE stock_entry_item (
    id             BIGSERIAL PRIMARY KEY,
    stock_entry_id BIGINT  NOT NULL REFERENCES stock_entry (id) ON DELETE CASCADE,
    lot_id         BIGINT  NOT NULL REFERENCES lot (id),
    quantity       INTEGER NOT NULL CHECK (quantity > 0),
    UNIQUE (stock_entry_id, lot_id)
);

-- Movements created by an entry (and by its corrections) point to it
ALTER TABLE stock_movement ADD COLUMN stock_entry_id BIGINT REFERENCES stock_entry (id) ON DELETE SET NULL;
CREATE INDEX idx_mov_stock_entry ON stock_movement (stock_entry_id) WHERE stock_entry_id IS NOT NULL;

-- New movement type for entry corrections
ALTER TABLE stock_movement DROP CONSTRAINT stock_movement_type_check;
ALTER TABLE stock_movement ADD CONSTRAINT stock_movement_type_check
    CHECK (type IN ('ENTRY', 'ENTRY_CORRECTION', 'INVENTORY_ADJUSTMENT', 'REPLENISHMENT',
                    'SURGERY_WITHDRAWAL', 'SURGERY_REVERSAL', 'LOAN'));
