-- V6 - Users log in by e-mail (with CPF and mobile phone), first access by invitation,
--      materials linked to one or more product lines, supplier orders without status.

-- ---------------------------------------------------------------- users
-- The name is no longer unique; the e-mail is the login and the unique identifier.
ALTER TABLE users RENAME COLUMN username TO name;
ALTER TABLE users DROP CONSTRAINT users_username_key;
UPDATE users SET email = LOWER(TRIM(email));
-- Users created without e-mail (should not exist) get a placeholder to be corrected by an administrator.
UPDATE users SET email = 'user' || id || '@pending.local' WHERE email IS NULL OR email = '';
ALTER TABLE users ALTER COLUMN email SET NOT NULL;

-- CPF (11 digits, validated by the application) and mobile phone (DDD + 9 digits).
-- Required by the application for every new or edited user; existing users fill them in later.
ALTER TABLE users ADD COLUMN cpf   VARCHAR(11) UNIQUE CHECK (cpf ~ '^[0-9]{11}$');
ALTER TABLE users ADD COLUMN phone VARCHAR(11) CHECK (phone ~ '^[1-9][1-9]9[0-9]{8}$');

-- Invited users have no password until they open the first-access link.
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

-- Sessions issued before this instant are rejected (password change ends every open session).
ALTER TABLE users ADD COLUMN sessions_valid_after TIMESTAMP;

-- The same token table serves the first-access invitation and the password reset.
ALTER TABLE password_reset_tokens ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'PASSWORD_RESET'
    CHECK (purpose IN ('INVITATION', 'PASSWORD_RESET'));

-- ---------------------------------------------------------------- materials
-- A material belongs to one or more product lines (e.g. bone cement: hip, knee and shoulder).
CREATE TABLE material_product_line (
    material_id  BIGINT      NOT NULL REFERENCES material (id) ON DELETE CASCADE,
    product_line VARCHAR(20) NOT NULL CHECK (product_line IN ('HIP', 'KNEE', 'SHOULDER')),
    PRIMARY KEY (material_id, product_line)
);

-- ---------------------------------------------------------------- supplier orders
-- Orders are only generated as PDF; the "sent" status is no longer used.
ALTER TABLE supplier_order DROP COLUMN status;
ALTER TABLE supplier_order DROP COLUMN sent_at;
