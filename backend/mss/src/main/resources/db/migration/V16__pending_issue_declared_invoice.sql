-- V16 - The invoice the box was declared as, on the pending issue it produced.
--
-- With the invoice required at the entry, a lot is linked only to the invoices whose DANFE was scanned.
-- What a lot that matched none of them needs, to be settled later, is the number the person had in hand -
-- and that number lived nowhere: the scanned keys only existed while the entry was being saved.
--
-- It is the declared number, not a link to an invoice row, on purpose. The usual reason a lot ends up here
-- is that the XML has not arrived yet, so there is no row to point at; and when the XML does arrive, the
-- administrator confirms the number that is already on the screen instead of remembering which box it was.

ALTER TABLE pending_issue ADD COLUMN declared_invoice VARCHAR(120);
