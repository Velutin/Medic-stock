-- =====================================================================
-- V3 - Surgical materials inventory domain
-- =====================================================================

-- Served hospitals. price_table_type is the source of the values (SIGTAP or TENDER).
CREATE TABLE hospital (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(150) NOT NULL UNIQUE,
    acronym          VARCHAR(30) UNIQUE,
    price_table_type VARCHAR(20) CHECK (price_table_type IN ('SIGTAP', 'TENDER')),
    active           BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Product lines served per hospital (hip, knee, shoulder).
CREATE TABLE hospital_product_line (
    hospital_id  BIGINT      NOT NULL REFERENCES hospital (id) ON DELETE CASCADE,
    product_line VARCHAR(20) NOT NULL CHECK (product_line IN ('HIP', 'KNEE', 'SHOULDER')),
    PRIMARY KEY (hospital_id, product_line)
);

-- Hospitals each user (surgical tech) works at.
CREATE TABLE user_hospital (
    user_id     BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    hospital_id BIGINT NOT NULL REFERENCES hospital (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, hospital_id)
);

-- Material catalog, identified by REF.
CREATE TABLE material (
    id           BIGSERIAL PRIMARY KEY,
    ref          VARCHAR(60)  NOT NULL UNIQUE,
    description  VARCHAR(255) NOT NULL,
    gtin         VARCHAR(14)  UNIQUE,   -- GS1 AI (01): same on every unit of the REF, stored with 14 digits
    component    VARCHAR(100),
    size         VARCHAR(30),
    color        VARCHAR(20),          -- size identification color (e.g. #FFD700)
    active       BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Material value per hospital (SIGTAP or tender table).
CREATE TABLE hospital_price (
    id          BIGSERIAL PRIMARY KEY,
    hospital_id BIGINT         NOT NULL REFERENCES hospital (id) ON DELETE CASCADE,
    material_id BIGINT         NOT NULL REFERENCES material (id),
    value       NUMERIC(12, 2) NOT NULL CHECK (value >= 0),
    updated_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (hospital_id, material_id)
);

-- Lots of each material.
CREATE TABLE lot (
    id          BIGSERIAL PRIMARY KEY,
    material_id BIGINT      NOT NULL REFERENCES material (id),
    number      VARCHAR(80) NOT NULL,
    expiry_date DATE        NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (material_id, number)
);
CREATE INDEX idx_lot_number ON lot (UPPER(number));

-- Balance per lot, hospital and location.
-- location = STOREROOM -> material in the storeroom, assigned to the hospital
-- location = HOSPITAL  -> material already inside the hospital
CREATE TABLE stock (
    id          BIGSERIAL PRIMARY KEY,
    lot_id      BIGINT      NOT NULL REFERENCES lot (id),
    hospital_id BIGINT      NOT NULL REFERENCES hospital (id),
    location    VARCHAR(10) NOT NULL CHECK (location IN ('STOREROOM', 'HOSPITAL')),
    quantity    INTEGER     NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    version     BIGINT      NOT NULL DEFAULT 0,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (lot_id, hospital_id, location)
);
CREATE INDEX idx_stock_hospital ON stock (hospital_id, location);

-- Minimums per hospital and REF.
CREATE TABLE minimum_stock (
    id             BIGSERIAL PRIMARY KEY,
    hospital_id    BIGINT  NOT NULL REFERENCES hospital (id) ON DELETE CASCADE,
    material_id    BIGINT  NOT NULL REFERENCES material (id),
    hospital_ideal INTEGER NOT NULL DEFAULT 0 CHECK (hospital_ideal >= 0),
    ideal_total    INTEGER NOT NULL DEFAULT 0 CHECK (ideal_total >= 0),
    UNIQUE (hospital_id, material_id),
    CHECK (ideal_total >= hospital_ideal)
);

-- Surgeries and consumed items.
CREATE TABLE surgery (
    id               BIGSERIAL PRIMARY KEY,
    hospital_id      BIGINT       NOT NULL REFERENCES hospital (id),
    surgical_tech_id BIGINT REFERENCES users (id),
    patient_name     VARCHAR(200) NOT NULL,
    surgery_date     DATE         NOT NULL,
    doctor           VARCHAR(200),
    status           VARCHAR(20)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELLED')),
    total_value      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    sheet_file       VARCHAR(500),
    notes            TEXT,
    created_by       BIGINT REFERENCES users (id),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP
);
CREATE INDEX idx_surgery_hospital_date ON surgery (hospital_id, surgery_date);
CREATE INDEX idx_surgery_date ON surgery (surgery_date);

CREATE TABLE surgery_item (
    id          BIGSERIAL PRIMARY KEY,
    surgery_id  BIGINT      NOT NULL REFERENCES surgery (id) ON DELETE CASCADE,
    lot_id      BIGINT      NOT NULL REFERENCES lot (id),
    quantity    INTEGER     NOT NULL CHECK (quantity > 0),
    unit_value  NUMERIC(12, 2),
    read_source VARCHAR(20) NOT NULL CHECK (read_source IN ('QR_CODE', 'BARCODE', 'MANUAL', 'CONSUMPTION_SHEET')),
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_surgery_item_surgery ON surgery_item (surgery_id);

-- Pending issues raised at withdrawal (unknown lot, no balance, expired, etc.).
CREATE TABLE pending_issue (
    id           BIGSERIAL PRIMARY KEY,
    surgery_id   BIGINT REFERENCES surgery (id) ON DELETE CASCADE,
    hospital_id  BIGINT       NOT NULL REFERENCES hospital (id),
    entered_code VARCHAR(200) NOT NULL,
    entered_ref  VARCHAR(60),
    quantity     INTEGER      NOT NULL DEFAULT 1,
    read_source  VARCHAR(20),
    reason       VARCHAR(30)  NOT NULL CHECK (reason IN ('LOT_NOT_FOUND', 'AMBIGUOUS_LOT', 'NO_HOSPITAL_BALANCE', 'EXPIRED_LOT')),
    status       VARCHAR(20)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'DISCARDED')),
    resolution   TEXT,
    resolved_by  BIGINT REFERENCES users (id),
    resolved_at  TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_pending_issue_status ON pending_issue (status);

-- Loans between hospitals (source: storeroom or hospital).
CREATE TABLE loan (
    id                      BIGSERIAL PRIMARY KEY,
    source_hospital_id      BIGINT      NOT NULL REFERENCES hospital (id),
    source_location         VARCHAR(10) NOT NULL CHECK (source_location IN ('STOREROOM', 'HOSPITAL')),
    destination_hospital_id BIGINT      NOT NULL REFERENCES hospital (id),
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING_NOTIFICATION' CHECK (status IN ('PENDING_NOTIFICATION', 'NOTIFIED')),
    notes                   TEXT,
    notified_at             TIMESTAMP,
    created_by              BIGINT REFERENCES users (id),
    created_at              TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (source_hospital_id <> destination_hospital_id)
);

CREATE TABLE loan_item (
    id       BIGSERIAL PRIMARY KEY,
    loan_id  BIGINT  NOT NULL REFERENCES loan (id) ON DELETE CASCADE,
    lot_id   BIGINT  NOT NULL REFERENCES lot (id),
    quantity INTEGER NOT NULL CHECK (quantity > 0)
);

-- Deliveries (storeroom -> hospital replenishment), basis of the delivery report.
CREATE TABLE delivery (
    id          BIGSERIAL PRIMARY KEY,
    hospital_id BIGINT    NOT NULL REFERENCES hospital (id),
    notes       TEXT,
    created_by  BIGINT REFERENCES users (id),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE delivery_item (
    id          BIGSERIAL PRIMARY KEY,
    delivery_id BIGINT  NOT NULL REFERENCES delivery (id) ON DELETE CASCADE,
    lot_id      BIGINT  NOT NULL REFERENCES lot (id),
    quantity    INTEGER NOT NULL CHECK (quantity > 0)
);

-- Material orders to the supplier (sent as PDF via WhatsApp).
CREATE TABLE supplier_order (
    id          BIGSERIAL PRIMARY KEY,
    hospital_id BIGINT      NOT NULL REFERENCES hospital (id),
    status      VARCHAR(20) NOT NULL DEFAULT 'GENERATED' CHECK (status IN ('GENERATED', 'SENT')),
    notes       TEXT,
    created_by  BIGINT REFERENCES users (id),
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at     TIMESTAMP
);

CREATE TABLE supplier_order_item (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT  NOT NULL REFERENCES supplier_order (id) ON DELETE CASCADE,
    material_id BIGINT  NOT NULL REFERENCES material (id),
    quantity    INTEGER NOT NULL CHECK (quantity > 0),
    urgent      BOOLEAN NOT NULL DEFAULT TRUE
);

-- Ledger of every stock movement.
CREATE TABLE stock_movement (
    id                      BIGSERIAL PRIMARY KEY,
    type                    VARCHAR(30) NOT NULL CHECK (type IN ('ENTRY', 'INVENTORY_ADJUSTMENT', 'REPLENISHMENT', 'SURGERY_WITHDRAWAL', 'SURGERY_REVERSAL', 'LOAN')),
    lot_id                  BIGINT      NOT NULL REFERENCES lot (id),
    quantity                INTEGER     NOT NULL,
    source_hospital_id      BIGINT REFERENCES hospital (id),
    source_location         VARCHAR(10),
    destination_hospital_id BIGINT REFERENCES hospital (id),
    destination_location    VARCHAR(10),
    surgery_id              BIGINT REFERENCES surgery (id) ON DELETE SET NULL,
    loan_id                 BIGINT REFERENCES loan (id) ON DELETE SET NULL,
    delivery_id             BIGINT REFERENCES delivery (id) ON DELETE SET NULL,
    user_id                 BIGINT REFERENCES users (id),
    notes                   TEXT,
    created_at              TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_mov_lot ON stock_movement (lot_id);
CREATE INDEX idx_mov_created_at ON stock_movement (created_at);
