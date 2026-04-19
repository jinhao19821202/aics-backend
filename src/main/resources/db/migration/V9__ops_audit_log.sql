-- 运营平台审计日志（跨租户；记录 ops_user 的敏感操作）
CREATE TABLE IF NOT EXISTS ops_audit_log (
    id             BIGSERIAL PRIMARY KEY,
    actor_id       BIGINT,
    actor_username VARCHAR(64),
    action         VARCHAR(64)  NOT NULL,
    target_type    VARCHAR(32)  NOT NULL,
    target_key     VARCHAR(128),
    before_val     JSONB,
    after_val      JSONB,
    ip             VARCHAR(64),
    user_agent     VARCHAR(255),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ops_audit_created_at ON ops_audit_log (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ops_audit_action     ON ops_audit_log (action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ops_audit_actor      ON ops_audit_log (actor_id, created_at DESC);
