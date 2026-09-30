-- Served hospitals and the product lines of each one.
-- price_table_type is left empty: set SIGTAP or TENDER via PUT /hospitals/{id}.
INSERT INTO hospital (name, acronym) VALUES
    ('Hospital Ortopédico', 'ORTOPEDICO'),
    ('Hupes', 'HUPES'),
    ('Hospital Costa do Cacau', 'COSTA_CACAU')
ON CONFLICT (name) DO NOTHING;

INSERT INTO hospital_product_line (hospital_id, product_line)
SELECT h.id, l.product_line
FROM hospital h
JOIN (VALUES
    ('ORTOPEDICO', 'HIP'), ('ORTOPEDICO', 'SHOULDER'),
    ('HUPES', 'KNEE'), ('HUPES', 'HIP'), ('HUPES', 'SHOULDER'),
    ('COSTA_CACAU', 'HIP')
) AS l (acronym, product_line) ON l.acronym = h.acronym
ON CONFLICT DO NOTHING;
