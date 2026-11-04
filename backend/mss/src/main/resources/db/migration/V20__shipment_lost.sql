-- V20 - A whole box can be lost.
--
-- The V19 gave a shipment three endings: it arrived, it was called off, or it is still travelling. What was
-- missing is the one that is actually most likely when something goes wrong in the post - the box never
-- shows up at all. Until now that had to be typed as "zero arrived" on every line, one at a time, and then
-- settled again as a loss on every pending issue it created. Forty items meant eighty entries to record one
-- event.
--
-- LOST is that event, recorded once. The balance leaves the road and leaves the stock, with a movement per
-- lot, so the history of each lot still says what happened to it.

ALTER TABLE delivery DROP CONSTRAINT delivery_status_check;
ALTER TABLE delivery ADD CONSTRAINT delivery_status_check
    CHECK (status IN ('DISPATCHED', 'ARRIVED', 'CANCELLED', 'LOST'));

ALTER TABLE loan DROP CONSTRAINT loan_status_check;
ALTER TABLE loan ADD CONSTRAINT loan_status_check
    CHECK (status IN ('DISPATCHED', 'ARRIVED', 'CANCELLED', 'LOST'));
