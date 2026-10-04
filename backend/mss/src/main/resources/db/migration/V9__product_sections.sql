-- V9 - Product sections (e.g. "Quadril não cimentada", "Quadril cimentada", "Bipolar"), registered by the user.
-- The hospital stock "by material" groups items by section, in display_order.

CREATE TABLE product_section (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(80) NOT NULL,
    display_order INTEGER     NOT NULL DEFAULT 0,
    active        BOOLEAN     NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uk_product_section_name ON product_section (LOWER(name));

ALTER TABLE material ADD COLUMN section_id BIGINT REFERENCES product_section (id);
CREATE INDEX idx_material_section ON material (section_id);

-- Initial sections, from the hospitals' stock spreadsheet (can be renamed, reordered or removed in Cadastros)
INSERT INTO product_section (name, display_order) VALUES
    ('Quadril não cimentada', 1),
    ('Quadril cimentada', 2),
    ('Bipolar', 3),
    ('Artroplastia de joelho', 4),
    ('Cirurgia de ombro', 5);
