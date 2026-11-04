-- V18 - Material that left the storeroom and has not reached the hospital yet.
--
-- Until now a lot was either in a storeroom or inside a hospital, and a transfer moved it from one to the
-- other in a single transaction. That is not how the material travels: a box separated today leaves
-- tomorrow, and one sent by post takes days. In that window the system claimed the hospital already had
-- material that was still on the road - and when a box was lost, it claimed so forever, because nothing
-- ever contradicted the transfer. Only a physical count would find it.
--
-- IN_TRANSIT is that third place. The balance leaves the origin when the box is dispatched and only enters
-- the hospital when someone confirms it arrived.
--
-- This migration ONLY opens the door: it widens the constraint so the value can exist. Nothing writes
-- IN_TRANSIT yet, so no balance moves and no screen changes. The flow that uses it comes separately, which
-- keeps this change free of any behaviour to regress.

ALTER TABLE stock DROP CONSTRAINT stock_location_check;
ALTER TABLE stock ADD CONSTRAINT stock_location_check
    CHECK (location IN ('STOREROOM', 'HOSPITAL', 'IN_TRANSIT'));

-- Deliberately NOT touched:
--
--   loan.source_location  - a loan is dispatched FROM a storeroom or FROM inside a hospital, never from
--                           the road, so its constraint stays as it is.
--   stock_movement        - its source_location and destination_location have no constraint at all, so a
--                           movement into or out of the new place is already accepted.
--
-- stock keeps UNIQUE (lot_id, hospital_id, location), which now allows one more row per lot and hospital:
-- the units of that lot travelling to it. The index on (hospital_id, location) already covers the lookup.
