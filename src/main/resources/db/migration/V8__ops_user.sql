-- 运营平台账号（跨租户，无 tenant_id）。
-- role = ops_admin | ops_support；鉴权由 backend-ops 的 iss=aics-ops JWT 承担。
CREATE TABLE IF NOT EXISTS ops_user (
    id               BIGSERIAL PRIMARY KEY,
    username         VARCHAR(64)  NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    display_name     VARCHAR(100),
    email            VARCHAR(100),
    role             VARCHAR(32)  NOT NULL DEFAULT 'ops_support',
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_ops_user_username UNIQUE (username),
    CONSTRAINT ck_ops_user_role CHECK (role IN ('ops_admin', 'ops_support'))
);

-- 初始运营超管（密码: Admin@123；哈希同 V3）
INSERT INTO ops_user (username, password_hash, display_name, email, role, enabled)
VALUES ('opsadmin', '$2a$10$ajpHv0jXmkCUPfwJoqgsteRbA1YP6dlm8k4XY1srwbTEP2r7ztd6y',
        '运营超管', 'ops@example.com', 'ops_admin', TRUE)
ON CONFLICT (username) DO NOTHING;
