-- V7 - Surgery completion date (billing period) and configurable billing rates.

-- Billing counts a surgery in the month it was completed. Surgeries already completed get the
-- best available approximation: the last update (or the creation) date.
ALTER TABLE surgery ADD COLUMN completed_at TIMESTAMP;
UPDATE surgery SET completed_at = COALESCE(updated_at, created_at) WHERE status = 'COMPLETED';
CREATE INDEX idx_surgery_completed_at ON surgery (completed_at) WHERE completed_at IS NOT NULL;

-- Billing rates with validity: each rate applies from the first day of its month until the next rate.
--   commission_rate: commission over the surgery total (e.g. 20%);
--   share_rate: the share over the commission (e.g. 8.5% of the 20%).
-- A surgery uses the rate valid in the month it was completed, so a new rate never changes past months.
CREATE TABLE billing_rate (
    id              BIGSERIAL PRIMARY KEY,
    valid_from      DATE         NOT NULL UNIQUE CHECK (EXTRACT(DAY FROM valid_from) = 1),
    commission_rate NUMERIC(5, 2) NOT NULL CHECK (commission_rate >= 0 AND commission_rate <= 100),
    share_rate      NUMERIC(5, 2) NOT NULL CHECK (share_rate >= 0 AND share_rate <= 100),
    created_by      BIGINT REFERENCES users (id),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Current rates (20% commission, 8.5% share over the commission), valid since the beginning.
INSERT INTO billing_rate (valid_from, commission_rate, share_rate) VALUES ('2000-01-01', 20.00, 8.50);

-- Fast lookup of surgery items still without a price (filled automatically when the price is registered).
CREATE INDEX idx_surgery_item_missing_price ON surgery_item (lot_id) WHERE unit_value IS NULL;
