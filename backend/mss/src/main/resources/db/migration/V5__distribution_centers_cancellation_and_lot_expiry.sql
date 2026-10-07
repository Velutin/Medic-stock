-- V5 - Distribution centers (e.g. SESAB), surgery cancellation data and lots identified by expiry date
--
-- A distribution center receives material for a group of hospitals (e.g. a SESAB tender
-- invoiced to SESAB). The material stays in the storeroom, like any other storeroom item,
-- but it can be delivered to any of the covered hospitals instead of a single one.
-- That delivery is a regular replenishment; after it, the material belongs to the hospital.

ALTER TABLE hospital ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'HOSPITAL'
    CHECK (type IN ('HOSPITAL', 'DISTRIBUTION_CENTER'));

-- Hospitals covered by each distribution center (a hospital belongs to at most one center).
CREATE TABLE distribution_center_hospital (
    center_id   BIGINT NOT NULL REFERENCES hospital (id) ON DELETE CASCADE,
    hospital_id BIGINT NOT NULL UNIQUE REFERENCES hospital (id),
    PRIMARY KEY (center_id, hospital_id),
    CHECK (center_id <> hospital_id)
);

-- Deliveries record which storeroom owner the material came from (null = the hospital itself).
ALTER TABLE delivery ADD COLUMN source_hospital_id BIGINT REFERENCES hospital (id);


-- Cancelled surgeries keep their items as a record; the cancellation has its own data.
ALTER TABLE surgery ADD COLUMN cancelled_at        TIMESTAMP;
ALTER TABLE surgery ADD COLUMN cancelled_by        BIGINT REFERENCES users (id);
ALTER TABLE surgery ADD COLUMN cancellation_reason VARCHAR(500);

-- Surgeries cancelled before this migration: the reason was stored in notes.
UPDATE surgery
   SET cancelled_at        = COALESCE(updated_at, created_at),
       cancellation_reason = LEFT(COALESCE(NULLIF(notes, ''), 'Not informed'), 500)
 WHERE status = 'CANCELLED';

ALTER TABLE surgery ADD CONSTRAINT surgery_cancellation_check
    CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL));

-- Units of the same lot can be sterilized on different days and therefore have different expiry dates.
-- A lot is now identified by material + number + expiry date; each combination has its own balance.
ALTER TABLE lot DROP CONSTRAINT lot_material_id_number_key;
ALTER TABLE lot ADD CONSTRAINT lot_material_number_expiry_key UNIQUE (material_id, number, expiry_date);
