-- ==========================================================
-- V4 P002 M1 租户核心表
-- 新增：tenant / tenant_wecom_app / tenant_llm_config / ops_access_log / kb_playground_log
-- ==========================================================

CREATE TABLE tenant (
    id                   BIGSERIAL PRIMARY KEY,
    code                 VARCHAR(32) NOT NULL UNIQUE,
    name                 VARCHAR(128) NOT NULL,
    status               VARCHAR(16) NOT NULL DEFAULT 'active',        -- active / suspended / archived
    contact_name         VARCHAR(64),
    contact_phone        VARCHAR(32),
    plan                 VARCHAR(32) NOT NULL DEFAULT 'basic',         -- basic / pro / enterprise
    quota_kb_docs        INTEGER NOT NULL DEFAULT 100,
    quota_monthly_tokens BIGINT  NOT NULL DEFAULT 10000000,
    milvus_collection    VARCHAR(64),                                  -- 当前 provisioned 的 Milvus collection 名
    embedding_dim        INTEGER,                                      -- 当前 collection 使用的向量维度
    reindex_status       VARCHAR(16) NOT NULL DEFAULT 'idle',          -- idle / running / failed
    reindex_last_error   TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_tenant_status ON tenant (status);

-- ------------------------------------------------------------
-- 企微应用配置（一个租户可多个自建应用）
-- ------------------------------------------------------------
CREATE TABLE tenant_wecom_app (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    corp_id         VARCHAR(64) NOT NULL,
    agent_id        INTEGER NOT NULL,
    token           VARCHAR(128) NOT NULL,
    aes_key_cipher  TEXT NOT NULL,
    secret_cipher   TEXT NOT NULL,
    bot_userid      VARCHAR(64),
    api_base        VARCHAR(255),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_wecom_corp_agent UNIQUE (corp_id, agent_id)
);
CREATE INDEX idx_wecom_app_tenant ON tenant_wecom_app (tenant_id);

-- ------------------------------------------------------------
-- 租户大模型配置
-- ------------------------------------------------------------
CREATE TABLE tenant_llm_config (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    provider        VARCHAR(32) NOT NULL,                  -- dashscope / openai / azure_openai / moonshot / custom
    purpose         VARCHAR(16) NOT NULL,                  -- chat / embedding
    api_key_cipher  TEXT NOT NULL,
    api_key_tail    VARCHAR(8),
    base_url        VARCHAR(255),
    model           VARCHAR(64) NOT NULL,
    embedding_dim   INTEGER,                               -- purpose=embedding 时必填
    params          JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    last_test_at    TIMESTAMPTZ,
    last_test_ok    BOOLEAN,
    last_test_msg   TEXT,
    created_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_llm_config_tenant ON tenant_llm_config (tenant_id, purpose);
-- 同租户同 purpose 下只能有一个 默认且启用 的配置
CREATE UNIQUE INDEX uq_tenant_llm_default ON tenant_llm_config (tenant_id, purpose)
    WHERE is_default = TRUE AND enabled = TRUE;

-- ------------------------------------------------------------
-- 运营访问审计（运营人员对租户数据的访问）
-- ------------------------------------------------------------
CREATE TABLE ops_access_log (
    id            BIGSERIAL PRIMARY KEY,
    ops_user_id   BIGINT NOT NULL,
    tenant_id     BIGINT,
    action        VARCHAR(64) NOT NULL,
    target_type   VARCHAR(32),
    target_id     VARCHAR(64),
    ip            VARCHAR(64),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ops_access_tenant_time ON ops_access_log (tenant_id, created_at DESC);
CREATE INDEX idx_ops_access_user_time   ON ops_access_log (ops_user_id, created_at DESC);

-- ------------------------------------------------------------
-- 召回调试日志
-- ------------------------------------------------------------
CREATE TABLE kb_playground_log (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    user_id       BIGINT,
    query         TEXT NOT NULL,
    channel       VARCHAR(16) NOT NULL DEFAULT 'both',
    top_k         INTEGER NOT NULL DEFAULT 10,
    threshold     NUMERIC(4,3) NOT NULL DEFAULT 0.5,
    hit_count     INTEGER NOT NULL DEFAULT 0,
    top_score     NUMERIC(6,4),
    latency_ms    INTEGER,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_kb_pg_log_tenant_time ON kb_playground_log (tenant_id, created_at DESC);
