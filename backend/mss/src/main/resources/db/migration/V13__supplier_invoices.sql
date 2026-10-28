-- V13 - Supplier invoices read from the e-mail box, and the material they announce.
--
-- The supplier (Baumer, Implus) e-mails the NF-e XML when the material leaves the factory, and later a second
-- message with the tracking code. Until the material is received, the invoice is what the system knows about
-- what is on its way: an invoice still open after a few days is material that did not arrive, which is the
-- hole an inventory audit cannot see today.
--
-- Nothing here is stock. The lines below never take part in a balance, a report or a value: they are the
-- announcement of material, and the stock still starts at the entry, from the label on the box.

-- --------------------------------------------------------------------------------------------- settings
-- Values the administrator changes from the screen instead of a new release: the reading hour, the number of
-- days before an open invoice becomes an alert and the switch that will make the invoice mandatory at the entry.
CREATE TABLE app_setting (
    key        VARCHAR(60) PRIMARY KEY,
    value      VARCHAR(255) NOT NULL,
    updated_by BIGINT REFERENCES users (id),
    updated_at TIMESTAMP
);

INSERT INTO app_setting (key, value) VALUES
    ('invoice.reading.hour', '3'),          -- hour of the day the mailbox is read (0-23, server time)
    ('invoice.alert.days', '7'),            -- an invoice open for longer than this is counted as not received
    ('invoice.required.on.entry', 'false'); -- true: an entry without a linked invoice is refused

-- --------------------------------------------------------------------------------------------- suppliers
-- Which emitters are read. The CNPJ is the criterion, never the name: the same company changes its trade name
-- and its operation description, the registration number does not. A CNPJ missing here means the invoice is
-- filed as "not a shipment" instead of being silently dropped.
CREATE TABLE invoice_supplier (
    id         BIGSERIAL PRIMARY KEY,
    cnpj       VARCHAR(14)  NOT NULL UNIQUE,
    name       VARCHAR(120) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO invoice_supplier (cnpj, name) VALUES
    ('61374161000130', 'BAUMER S.A.'),
    ('00426264000101', 'IMPLUS LTDA');

-- Shipment CFOPs: what is coming to us. A return (2918) is not here on purpose, nor is any CFOP absent from
-- this list: an unknown one is filed for review rather than guessed.
CREATE TABLE invoice_shipment_cfop (
    cfop VARCHAR(4) PRIMARY KEY
);

INSERT INTO invoice_shipment_cfop (cfop) VALUES
    ('5917'),  -- remessa em consignação, mesma UF
    ('6917'),  -- remessa em consignação, interestadual (Baumer e Implus hoje)
    ('5949'),
    ('6949');

-- --------------------------------------------------------------------------------------------- invoices
-- status:
--   AWAITING_XML - we know the invoice exists (the tracking e-mail named it) but have not read its XML yet
--   OPEN         - items announced, nothing received
--   PARTIAL      - part received; it stays an alert, the missing part is what is charged to the supplier
--   RECEIVED     - everything received
--   CLOSED       - closed by the administrator with a reason (settled with the supplier, cancelled, lost)
--   NOT_SHIPMENT - read and filed: it is not material coming to us (a return, an unknown CFOP)
CREATE TABLE supplier_invoice (
    id              BIGSERIAL PRIMARY KEY,
    -- NF-e access key, 44 digits. It is the identity of the invoice: it carries the emitter's CNPJ, the series
    -- and the number, and its last digit checks the other 43. Unique, so the same XML read twice is one row.
    access_key      VARCHAR(44) UNIQUE,
    supplier_id     BIGINT REFERENCES invoice_supplier (id),
    -- Kept as text as well: a tracking e-mail may name an invoice before its XML arrives
    emitter_cnpj    VARCHAR(14),
    emitter_name    VARCHAR(120),
    number          VARCHAR(20)  NOT NULL,
    series          VARCHAR(5),
    issued_at       TIMESTAMP,
    operation       VARCHAR(120),
    cfop            VARCHAR(4),
    -- Destination as printed on the invoice. It is information, not a rule: the hospital named there may be the
    -- one the material is for, and the delivery address is ours anyway.
    recipient_name  VARCHAR(140),
    recipient_cnpj  VARCHAR(14),
    carrier_name    VARCHAR(140),
    tracking_code   VARCHAR(40),
    -- Surgery date read from the invoice when it carries one (OPME shipments). Patient and surgeon are not
    -- stored: they are other people's data and add nothing to the control of what arrived.
    surgery_date    DATE,
    total_value     NUMERIC(14, 2),
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    received_at     DATE,
    closed_at       TIMESTAMP,
    closed_by       BIGINT REFERENCES users (id),
    close_reason    VARCHAR(255),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    CONSTRAINT supplier_invoice_status_check
        CHECK (status IN ('AWAITING_XML', 'OPEN', 'PARTIAL', 'RECEIVED', 'CLOSED', 'NOT_SHIPMENT')),
    -- Emitter + series + number identifies an invoice when the key is not known yet: only the number would
    -- collide between suppliers (Baumer 242466 and Implus 6886 are both series 2).
    CONSTRAINT supplier_invoice_number_unique UNIQUE (emitter_cnpj, series, number)
);
CREATE INDEX idx_invoice_status ON supplier_invoice (status);
CREATE INDEX idx_invoice_issued ON supplier_invoice (issued_at DESC, id DESC);
CREATE INDEX idx_invoice_tracking ON supplier_invoice (tracking_code) WHERE tracking_code IS NOT NULL;

-- --------------------------------------------------------------------------------------------- items
-- One row per line of the invoice, not per REF: the same REF appears many times in one invoice (144.B.68.36
-- shows up on four lines of a single Baumer invoice), sometimes with different values.
--
-- category:
--   IMPLANT    - the GTIN is in the catalog: it arrives with a label and is received by scanning it
--   INSTRUMENT - the GTIN is not in the catalog: it arrives loose, without a readable code, and is received
--                by confirmation. Registering the item at the entry turns it into an implant from then on.
CREATE TABLE supplier_invoice_item (
    id                 BIGSERIAL PRIMARY KEY,
    invoice_id         BIGINT      NOT NULL REFERENCES supplier_invoice (id) ON DELETE CASCADE,
    item_number        INTEGER     NOT NULL,
    product_code       VARCHAR(60),
    gtin               VARCHAR(14),
    description        VARCHAR(255),
    -- Catalog material when the GTIN (or the product code) matches one; null keeps the item identified by
    -- the invoice text alone, to be resolved by hand or by the first entry that registers it.
    material_id        BIGINT REFERENCES material (id),
    category           VARCHAR(12) NOT NULL DEFAULT 'INSTRUMENT',
    -- Quantity shipped, from qCom. It is NOT the sum of the lots below: the XML declares the sterilisation
    -- group of each lot, which is often larger than what was sent.
    quantity           INTEGER     NOT NULL CHECK (quantity > 0),
    received_quantity  INTEGER     NOT NULL DEFAULT 0 CHECK (received_quantity >= 0),
    unit_value         NUMERIC(14, 4),
    CONSTRAINT supplier_invoice_item_category_check CHECK (category IN ('IMPLANT', 'INSTRUMENT')),
    UNIQUE (invoice_id, item_number)
);
CREATE INDEX idx_invoice_item_invoice ON supplier_invoice_item (invoice_id);
CREATE INDEX idx_invoice_item_material ON supplier_invoice_item (material_id) WHERE material_id IS NOT NULL;

-- Lots the XML declares for an item: candidates, not a division of the quantity. With a single lot the item
-- is certain; with several, only the label scanned at the entry says which ones actually came.
CREATE TABLE supplier_invoice_item_lot (
    id              BIGSERIAL PRIMARY KEY,
    invoice_item_id BIGINT      NOT NULL REFERENCES supplier_invoice_item (id) ON DELETE CASCADE,
    lot_number      VARCHAR(80) NOT NULL,
    -- Lot number without leading zeros and in upper case, as the rest of the system compares lots
    -- (005706061 and 5706061 are one lot). The entry matches on this column.
    lot_key         VARCHAR(80) NOT NULL,
    manufactured_at DATE,
    expiry_date     DATE,
    quantity        INTEGER,
    UNIQUE (invoice_item_id, lot_number)
);
CREATE INDEX idx_invoice_lot_key ON supplier_invoice_item_lot (lot_key);

-- --------------------------------------------------------------------------------------------- the link
-- What an entry received against what an invoice announced. The link lives here and not on the lot: the same
-- physical lot arrives in more than one invoice, and a column on the lot would keep only the last one.
--
-- origin:
--   AUTO_LOT  - matched by the lot number when the entry was registered
--   MANUAL    - chosen or corrected by the administrator
--   CONFIRMED - instrument confirmed as received by the administrator, with no label to scan
CREATE TABLE stock_entry_item_invoice (
    id                  BIGSERIAL PRIMARY KEY,
    stock_entry_item_id BIGINT      REFERENCES stock_entry_item (id) ON DELETE CASCADE,
    invoice_item_id     BIGINT      NOT NULL REFERENCES supplier_invoice_item (id) ON DELETE CASCADE,
    quantity            INTEGER     NOT NULL CHECK (quantity > 0),
    origin              VARCHAR(12) NOT NULL,
    created_by          BIGINT REFERENCES users (id),
    created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT stock_entry_item_invoice_origin_check CHECK (origin IN ('AUTO_LOT', 'MANUAL', 'CONFIRMED')),
    -- An instrument is confirmed without an entry item (there is no label, so no lot enters this way)
    CONSTRAINT stock_entry_item_invoice_source_check
        CHECK (stock_entry_item_id IS NOT NULL OR origin = 'CONFIRMED')
);
CREATE INDEX idx_entry_invoice_item ON stock_entry_item_invoice (invoice_item_id);
CREATE INDEX idx_entry_invoice_entry ON stock_entry_item_invoice (stock_entry_item_id);

-- Invoices named by a tracking e-mail, so the entry of the implants can ask about the instruments that
-- travelled in the same box. Filled by the tracking reader; nothing here guesses a shipment from dates.
CREATE TABLE invoice_tracking_group (
    id            BIGSERIAL PRIMARY KEY,
    tracking_code VARCHAR(40) NOT NULL,
    invoice_id    BIGINT      NOT NULL REFERENCES supplier_invoice (id) ON DELETE CASCADE,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tracking_code, invoice_id)
);

-- --------------------------------------------------------------------------------------------- mailbox log
-- Every message the reader opens, including the ones it does not understand. A message that is read and
-- dropped in silence is how an invitation disappeared once; here nothing is dropped without a row saying why.
--
-- status: IMPORTED, DUPLICATE, NOT_SHIPMENT, UNRECOGNISED, FAILED
CREATE TABLE incoming_email (
    id           BIGSERIAL PRIMARY KEY,
    message_uid  VARCHAR(120) NOT NULL UNIQUE,
    sender       VARCHAR(255),
    subject      VARCHAR(255),
    sent_at      TIMESTAMP,
    read_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status       VARCHAR(20)  NOT NULL,
    attachments  INTEGER      NOT NULL DEFAULT 0,
    invoices     INTEGER      NOT NULL DEFAULT 0,
    detail       TEXT,
    CONSTRAINT incoming_email_status_check
        CHECK (status IN ('IMPORTED', 'DUPLICATE', 'NOT_SHIPMENT', 'UNRECOGNISED', 'FAILED'))
);
CREATE INDEX idx_incoming_email_read ON incoming_email (read_at DESC, id DESC);
CREATE INDEX idx_incoming_email_status ON incoming_email (status);
