-- V15 - A lot that entered the stock without an invoice behind it becomes a pending issue.
--
-- Until now, turning on "invoice required at the entry" refused the entry. Refusing was the wrong answer:
-- it threw away a box already unpacked and counted, and it punished the person at the bench for an e-mail
-- that had not arrived. The material enters; what is missing is the paperwork, and paperwork is settled
-- afterwards, by the administrator, not by re-scanning two hundred labels.
--
-- The pending issue is the one the system already has: the same table, the same screen, the same card on
-- the dashboard. Only the reason is new, and a column saying which entry item it is about - without it
-- there is no way to write the link when the invoice is finally informed.

ALTER TABLE pending_issue
    ADD COLUMN stock_entry_item_id BIGINT REFERENCES stock_entry_item (id) ON DELETE CASCADE;

CREATE INDEX idx_pending_entry_item ON pending_issue (stock_entry_item_id)
    WHERE stock_entry_item_id IS NOT NULL;

ALTER TABLE pending_issue DROP CONSTRAINT IF EXISTS pending_issue_reason_check;
ALTER TABLE pending_issue ADD CONSTRAINT pending_issue_reason_check
    CHECK (reason IN ('LOT_NOT_FOUND', 'AMBIGUOUS_LOT', 'NO_HOSPITAL_BALANCE', 'EXPIRED_LOT', 'WITHOUT_INVOICE'));
