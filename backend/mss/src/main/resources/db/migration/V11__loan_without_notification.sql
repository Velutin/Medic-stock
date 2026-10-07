-- V11 - Loans no longer track the supplier notification: the company is informed when the loan is made.

ALTER TABLE loan DROP CONSTRAINT loan_type_fields;
ALTER TABLE loan DROP COLUMN status;
ALTER TABLE loan DROP COLUMN notified_at;
ALTER TABLE loan ADD CONSTRAINT loan_type_fields CHECK (
    (type = 'LOAN' AND destination_hospital_id IS NOT NULL)
    OR (type = 'RETURN' AND destination_hospital_id IS NULL AND return_reason IS NOT NULL));
