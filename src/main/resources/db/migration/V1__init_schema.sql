-- ==========================================================
-- M001 消息接入与会话路由
-- ==========================================================
CREATE TABLE wecom_message (
    id              BIGSERIAL PRIMARY KEY,
    msg_id          VARCHAR(64) NOT NULL UNIQUE,
    group_id        VARCHAR(64) NOT NULL,
    from_userid     VARCHAR(64),
    from_name       VARCHAR(128),
    msg_type        VARCHAR(16) NOT NULL,
    content         TEXT,
    mentioned_list  JSONB,
    raw             TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_wecom_msg_group ON wecom_message (group_id, created_at DESC);

CREATE TABLE session_message (
    id           BIGSERIAL PRIMARY KEY,
    group_id     VARCHAR(64) NOT NULL,
    msg_id       VARCHAR(64) NOT NULL UNIQUE,
    user_id      VARCHAR(64),
    user_name    VARCHAR(128),
    role         VARCHAR(16) NOT NULL,  -- user / bot / agent
    text         TEXT NOT NULL,
    token_count  INTEGER,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_session_msg_group_time ON session_message (group_id, created_at DESC);

-- ==========================================================
-- M004 人工转接
-- ==========================================================
CREATE TABLE group_session (
    id                BIGSERIAL PRIMARY KEY,
    group_id          VARCHAR(64) NOT NULL UNIQUE,
    state             VARCHAR(32) NOT NULL DEFAULT 'BOT_ACTIVE',
    handoff_id        BIGINT,
    last_activity_at  TIMESTAMPTZ,
    version           INTEGER NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE handoff_record (
    id              BIGSERIAL PRIMARY KEY,
    group_id        VARCHAR(64) NOT NULL,
    trigger_reason  VARCHAR(32) NOT NULL,
    trigger_payload JSONB,
    triggered_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    agent_userid    VARCHAR(64),
    closed_at       TIMESTAMPTZ,
    close_reason    VARCHAR(32)
);
CREATE INDEX idx_handoff_group_time ON handoff_record (group_id, triggered_at DESC);
CREATE INDEX idx_handoff_open ON handoff_record (closed_at) WHERE closed_at IS NULL;

CREATE TABLE group_agent_mapping (
    id              BIGSERIAL PRIMARY KEY,
    group_id        VARCHAR(64) NOT NULL UNIQUE,
    group_name      VARCHAR(255),
    agent_userids   JSONB NOT NULL DEFAULT '[]'::jsonb,
    default_agent   VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ==========================================================
-- M003 知识库
-- ==========================================================
CREATE TABLE kb_document (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    source_type  VARCHAR(16) NOT NULL,  -- document / faq
    file_url     VARCHAR(1024),
    file_hash    VARCHAR(128),
    status       VARCHAR(16) NOT NULL DEFAULT 'parsing', -- parsing/ready/failed
    error_msg    TEXT,
    tags         JSONB DEFAULT '[]'::jsonb,
    chunk_count  INTEGER DEFAULT 0,
    created_by   BIGINT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted      BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_kb_document_status ON kb_document (status, deleted);

CREATE TABLE kb_chunk (
    id          BIGSERIAL PRIMARY KEY,
    doc_id      BIGINT NOT NULL REFERENCES kb_document(id) ON DELETE CASCADE,
    content     TEXT NOT NULL,
    milvus_id   BIGINT,
    meta        JSONB DEFAULT '{}'::jsonb,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_kb_chunk_doc ON kb_chunk (doc_id);
CREATE INDEX idx_kb_chunk_milvus ON kb_chunk (milvus_id);

CREATE TABLE kb_faq (
    id          BIGSERIAL PRIMARY KEY,
    question    VARCHAR(512) NOT NULL,
    answer      TEXT NOT NULL,
    keywords    VARCHAR(512),
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_by  BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_kb_faq_enabled ON kb_faq (enabled);

-- ==========================================================
-- M005 合规 / 审计 / 用户
-- ==========================================================
CREATE TABLE sensitive_word (
    id          BIGSERIAL PRIMARY KEY,
    word        VARCHAR(64) NOT NULL,
    category    VARCHAR(32) NOT NULL,   -- POLITICS/PORN/VIOLENCE/PRIVACY/COMPETITOR/CUSTOM
    level       VARCHAR(16) NOT NULL,   -- HIGH/MEDIUM/LOW
    action      VARCHAR(16) NOT NULL,   -- BLOCK/MASK/ALERT
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_by  BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (word, category)
);

CREATE TABLE sensitive_hit_log (
    id                BIGSERIAL PRIMARY KEY,
    direction         VARCHAR(16) NOT NULL,   -- INBOUND/OUTBOUND
    group_id          VARCHAR(64),
    word              VARCHAR(64) NOT NULL,
    level             VARCHAR(16) NOT NULL,
    action_taken      VARCHAR(16) NOT NULL,
    original_snippet  VARCHAR(500),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sens_hit_time ON sensitive_hit_log (created_at DESC);

CREATE TABLE llm_invocation (
    id                BIGSERIAL PRIMARY KEY,
    conversation_id   VARCHAR(64),
    group_id          VARCHAR(64) NOT NULL,
    trigger_msg_id    VARCHAR(64),
    model             VARCHAR(32) NOT NULL,
    prompt            TEXT NOT NULL,
    response          TEXT,
    prompt_tokens     INTEGER,
    completion_tokens INTEGER,
    latency_ms        INTEGER,
    references_used   JSONB,
    confidence        NUMERIC(4,3),
    handoff           BOOLEAN NOT NULL DEFAULT FALSE,
    status            VARCHAR(16) NOT NULL DEFAULT 'OK',  -- OK/FAIL/TIMEOUT
    error_msg         TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_llm_invoc_group_time ON llm_invocation (group_id, created_at DESC);
CREATE INDEX idx_llm_invoc_conv ON llm_invocation (conversation_id);

CREATE TABLE admin_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    admin_id        BIGINT,
    admin_name      VARCHAR(64),
    action          VARCHAR(64) NOT NULL,
    resource_type   VARCHAR(32),
    resource_id     VARCHAR(64),
    before_val      JSONB,
    after_val       JSONB,
    ip              VARCHAR(45),
    user_agent      VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_admin_time ON admin_audit_log (admin_id, created_at DESC);
CREATE INDEX idx_audit_action_time ON admin_audit_log (action, created_at DESC);

CREATE TABLE admin_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64) NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    display_name    VARCHAR(64),
    email           VARCHAR(128),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE admin_role (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(64) NOT NULL UNIQUE,
    description  VARCHAR(255),
    built_in     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE admin_permission (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(64) NOT NULL UNIQUE,
    description  VARCHAR(255)
);

CREATE TABLE user_role (
    user_id  BIGINT NOT NULL REFERENCES admin_user(id) ON DELETE CASCADE,
    role_id  BIGINT NOT NULL REFERENCES admin_role(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permission (
    role_id        BIGINT NOT NULL REFERENCES admin_role(id) ON DELETE CASCADE,
    permission_id  BIGINT NOT NULL REFERENCES admin_permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_group_scope (
    user_id   BIGINT NOT NULL REFERENCES admin_user(id) ON DELETE CASCADE,
    group_id  VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, group_id)
);

CREATE TABLE stat_snapshot (
    id                  BIGSERIAL PRIMARY KEY,
    date                DATE NOT NULL,
    group_id            VARCHAR(64),
    total_conversations INTEGER DEFAULT 0,
    self_resolved       INTEGER DEFAULT 0,
    handoff_count       INTEGER DEFAULT 0,
    avg_latency_ms      INTEGER DEFAULT 0,
    p95_latency_ms      INTEGER DEFAULT 0,
    prompt_tokens       BIGINT DEFAULT 0,
    completion_tokens   BIGINT DEFAULT 0,
    faq_hits            INTEGER DEFAULT 0,
    sensitive_hits      INTEGER DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uk_stat_snapshot_date_group ON stat_snapshot (date, COALESCE(group_id, ''));

-- 导出任务
CREATE TABLE export_task (
    id           BIGSERIAL PRIMARY KEY,
    admin_id     BIGINT NOT NULL,
    type         VARCHAR(32) NOT NULL, -- SESSIONS / LLM / AUDIT
    params       JSONB,
    status       VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
    file_url     VARCHAR(1024),
    error_msg    TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at  TIMESTAMPTZ
);
