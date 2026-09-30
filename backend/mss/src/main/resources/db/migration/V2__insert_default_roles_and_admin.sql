INSERT INTO roles (role) VALUES ('MASTER'), ('ADMIN'), ('USER'), ('SURGICAL_TECH')
ON CONFLICT (role) DO NOTHING;

-- Initial administrator user. Password: admin123 (BCrypt).
-- CHANGE THE PASSWORD on first login (PATCH /user/change-password).
INSERT INTO users (username, email, password)
VALUES ('admin', 'admin@mss.local', '$2a$12$sKV6i1GKNanbPQKn6cLHCOb2Fa6ngMsbjFJ/50tKeoaSmW.05dEGy')
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'admin' AND r.role IN ('USER', 'ADMIN', 'MASTER')
ON CONFLICT DO NOTHING;
