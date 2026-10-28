-- V14 - The DANFE that comes with the invoice, and tracking codes that arrive before their invoice.
--
-- Two small additions, together in one migration on purpose: the second part of the invoice work (linking
-- the entry to the invoice, reading the DANFE barcode, confirming instruments) needs no schema change at
-- all - V13 already left stock_entry_item_invoice and the settings table in place. So this is the last
-- migration of this feature.

-- --------------------------------------------------------------------------------------------- the PDF
-- The XML carries every fact; the PDF is the document to show the supplier in a disagreement. It is kept
-- for a while and then deleted on its own: what costs space is the file, not the invoice, so the invoice
-- stays forever and only the attachment expires.
ALTER TABLE supplier_invoice ADD COLUMN pdf_file VARCHAR(255);
ALTER TABLE supplier_invoice ADD COLUMN pdf_stored_at TIMESTAMP;
-- Set when the file is deleted by age, so the screen can say "expired" instead of "never had one"
ALTER TABLE supplier_invoice ADD COLUMN pdf_expired_at TIMESTAMP;

CREATE INDEX idx_invoice_pdf_age ON supplier_invoice (pdf_stored_at) WHERE pdf_file IS NOT NULL;

-- How long a PDF is kept, in days. A setting and not a constant: six months is today's answer, not a law.
INSERT INTO app_setting (key, value) VALUES ('invoice.pdf.retention.days', '180');

-- ------------------------------------------------------------------------------------- waiting tracking
-- A tracking e-mail names invoices by number. Almost always they are already registered, because the XML
-- travels first - but the order of two e-mails is not something to depend on. A number with no invoice yet
-- waits here, and the code is applied by itself when that invoice's XML is read.
--
-- No invoice is invented from a tracking e-mail: an invoice whose items nobody knows would be a row that
-- announces material without saying which, and that is worse than no row at all.
CREATE TABLE pending_tracking (
    id            BIGSERIAL PRIMARY KEY,
    -- Invoice number as the e-mail wrote it, without leading zeros
    number        VARCHAR(20) NOT NULL,
    tracking_code VARCHAR(40) NOT NULL,
    -- Emitter when it could be told from the other invoices of the same e-mail; null when it could not.
    -- The sender's domain is NOT used for this: a tracking e-mail from baumer.com.br arrived listing
    -- invoices issued by Implus, so the address says nothing about who issued them.
    emitter_cnpj  VARCHAR(14),
    source_email  VARCHAR(255),
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_at    TIMESTAMP,
    UNIQUE (number, tracking_code)
);
CREATE INDEX idx_pending_tracking_open ON pending_tracking (number) WHERE applied_at IS NULL;

-- ------------------------------------------------------------------------------------- mailbox outcomes
-- A message can now also be a tracking e-mail, which carries no attachment and is not "unrecognised".
ALTER TABLE incoming_email DROP CONSTRAINT incoming_email_status_check;
ALTER TABLE incoming_email ADD CONSTRAINT incoming_email_status_check
    CHECK (status IN ('IMPORTED', 'DUPLICATE', 'NOT_SHIPMENT', 'UNRECOGNISED', 'FAILED', 'TRACKING'));
