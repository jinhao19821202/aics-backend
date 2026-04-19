-- ==========================================================
-- V5 P002 M1 业务表多租户化
-- 所有业务表加 tenant_id 列，默认值 1（由 V7 seed 的默认租户）
-- 建复合索引加速按租户查询
-- ==========================================================

-- 为简化：统一先以 DEFAULT 1 加列，保证存量行不为 NULL；再移除 default 强制后续写入显式传值
DO $$
DECLARE
    t TEXT;
BEGIN
    FOR t IN SELECT unnest(ARRAY[
        'wecom_message',
        'session_message',
        'group_session',
        'handoff_record',
        'group_agent_mapping',
        'kb_document',
        'kb_chunk',
        'kb_faq',
        'admin_user',
        'user_group_scope',
        'admin_audit_log',
        'llm_invocation',
        'sensitive_word',
        'sensitive_hit_log',
        'stat_snapshot',
        'export_task'
    ])
    LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1', t);
        EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id DROP DEFAULT', t);
    END LOOP;
END $$;

-- 复合索引
CREATE INDEX idx_wecom_msg_tenant_group        ON wecom_message        (tenant_id, group_id, created_at DESC);
CREATE INDEX idx_session_msg_tenant_group      ON session_message      (tenant_id, group_id, created_at DESC);
CREATE INDEX idx_group_session_tenant          ON group_session        (tenant_id, group_id);
CREATE INDEX idx_handoff_tenant_time           ON handoff_record       (tenant_id, triggered_at DESC);
CREATE INDEX idx_group_agent_mapping_tenant    ON group_agent_mapping  (tenant_id, group_id);
CREATE INDEX idx_kb_document_tenant            ON kb_document          (tenant_id, status, deleted);
CREATE INDEX idx_kb_chunk_tenant               ON kb_chunk             (tenant_id);
CREATE INDEX idx_kb_faq_tenant                 ON kb_faq               (tenant_id, enabled);
CREATE INDEX idx_admin_user_tenant             ON admin_user           (tenant_id);
CREATE INDEX idx_admin_audit_tenant_time       ON admin_audit_log      (tenant_id, created_at DESC);
CREATE INDEX idx_llm_invoc_tenant_time         ON llm_invocation       (tenant_id, created_at DESC);
CREATE INDEX idx_sensitive_word_tenant         ON sensitive_word       (tenant_id, enabled);
CREATE INDEX idx_sensitive_hit_tenant_time     ON sensitive_hit_log    (tenant_id, created_at DESC);
CREATE INDEX idx_stat_snapshot_tenant_date     ON stat_snapshot        (tenant_id, date);
CREATE INDEX idx_export_task_tenant            ON export_task          (tenant_id, admin_id);

-- admin_user 原 UNIQUE(username) 需改为 UNIQUE(tenant_id, username)，允许不同租户重名
ALTER TABLE admin_user DROP CONSTRAINT admin_user_username_key;
CREATE UNIQUE INDEX uk_admin_user_tenant_username ON admin_user (tenant_id, username);

-- group_session 原 UNIQUE(group_id) 同理——群只可能属于一个租户，但为明确 tenant 维度，改为 (tenant_id, group_id)
ALTER TABLE group_session DROP CONSTRAINT group_session_group_id_key;
CREATE UNIQUE INDEX uk_group_session_tenant_group ON group_session (tenant_id, group_id);

-- group_agent_mapping 同理
ALTER TABLE group_agent_mapping DROP CONSTRAINT group_agent_mapping_group_id_key;
CREATE UNIQUE INDEX uk_group_agent_map_tenant_group ON group_agent_mapping (tenant_id, group_id);

-- sensitive_word 原 UNIQUE(word, category) 改为 (tenant_id, word, category)
ALTER TABLE sensitive_word DROP CONSTRAINT sensitive_word_word_category_key;
CREATE UNIQUE INDEX uk_sensitive_word_tenant ON sensitive_word (tenant_id, word, category);

-- stat_snapshot 原 UNIQUE 索引需要重建包含 tenant_id
DROP INDEX IF EXISTS uk_stat_snapshot_date_group;
CREATE UNIQUE INDEX uk_stat_snapshot_tenant_date_group
    ON stat_snapshot (tenant_id, date, COALESCE(group_id, ''));

-- wecom_message 原 UNIQUE(msg_id) 在企微语境下 msg_id 可能跨租户重复，改为 (tenant_id, msg_id)
ALTER TABLE wecom_message DROP CONSTRAINT wecom_message_msg_id_key;
CREATE UNIQUE INDEX uk_wecom_message_tenant_msgid ON wecom_message (tenant_id, msg_id);

-- session_message 同理
ALTER TABLE session_message DROP CONSTRAINT session_message_msg_id_key;
CREATE UNIQUE INDEX uk_session_message_tenant_msgid ON session_message (tenant_id, msg_id);
