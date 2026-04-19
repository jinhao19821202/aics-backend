-- P004-A F002 多智能客服（CS Agent）实体 + wecomApp 绑定：
-- 1) 新建 tenant_cs_agent 表；每租户多个 Agent；
-- 2) tenant_wecom_app.cs_agent_id 上加 FK + 条件唯一索引（已 V15 加列）；
-- 3) F002.7 老租户兼容迁移：有企微应用但无 Agent 的租户自动建一个默认 Agent，并把首个应用绑过去。
-- 4) 权限码 cs:agent:manage 挂到默认租户 SUPER_ADMIN；TENANT_OWNER 由 tenant bootstrap 服务动态绑定。

CREATE TABLE tenant_cs_agent (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name                VARCHAR(64) NOT NULL,
    code                VARCHAR(32) NOT NULL,
    description         VARCHAR(255),
    avatar_url          VARCHAR(255),
    persona_prompt      TEXT,
    greeting            VARCHAR(255),
    chat_llm_config_id  BIGINT,                    -- 软引用 tenant_llm_config.id；null=用租户默认
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    created_by          BIGINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_cs_agent_tenant_code UNIQUE (tenant_id, code)
);
CREATE INDEX idx_cs_agent_tenant ON tenant_cs_agent(tenant_id);

COMMENT ON TABLE tenant_cs_agent IS 'P004-A F002 智能客服实体；一租户可多个；独立配置 FAQ/文档范围（V18）、可选 chat LLM 覆盖。';
COMMENT ON COLUMN tenant_cs_agent.code IS '租户内唯一的英文 slug，如 hr_bot / sales_bot。';
COMMENT ON COLUMN tenant_cs_agent.persona_prompt IS '人设前缀，拼入 system prompt；覆盖全局 bot-name 描述。';
COMMENT ON COLUMN tenant_cs_agent.greeting IS '首次被 @ 或 @bot 带空内容时的引导语；覆盖租户默认。';
COMMENT ON COLUMN tenant_cs_agent.chat_llm_config_id IS '软引用 tenant_llm_config.id；null 则使用租户默认 chat 配置。embedding/rerank 本期不支持 Agent 级覆盖（见 PRD v1.1 评审修订）。';

-- 反向绑定 FK（V15 已加列）：同一 wecomApp 只能被一个 Agent 认领
ALTER TABLE tenant_wecom_app
    ADD CONSTRAINT fk_wecom_app_cs_agent FOREIGN KEY (cs_agent_id)
        REFERENCES tenant_cs_agent(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uk_wecom_app_cs_agent ON tenant_wecom_app(cs_agent_id)
    WHERE cs_agent_id IS NOT NULL;

-- ------------------------------------------------------------
-- F002.7 老租户兼容：有 wecomApp 但无 Agent 的租户，自动 seed 默认 Agent
-- ------------------------------------------------------------

INSERT INTO tenant_cs_agent (tenant_id, name, code, persona_prompt, enabled)
SELECT DISTINCT t.id, '默认助手', 'default_bot',
       '你是 ' || t.name || ' 的智能客服助手，根据知识库提供准确回答。',
       TRUE
FROM tenant t
WHERE EXISTS (SELECT 1 FROM tenant_wecom_app w WHERE w.tenant_id = t.id)
  AND NOT EXISTS (SELECT 1 FROM tenant_cs_agent a WHERE a.tenant_id = t.id);

-- 每租户把首个（id 最小的）wecomApp 绑到默认 Agent
UPDATE tenant_wecom_app w
SET cs_agent_id = (
    SELECT a.id FROM tenant_cs_agent a
    WHERE a.tenant_id = w.tenant_id AND a.code = 'default_bot'
    LIMIT 1
)
WHERE w.cs_agent_id IS NULL
  AND w.id = (
    SELECT MIN(w2.id) FROM tenant_wecom_app w2
    WHERE w2.tenant_id = w.tenant_id
  )
  AND EXISTS (
    SELECT 1 FROM tenant_cs_agent a
    WHERE a.tenant_id = w.tenant_id AND a.code = 'default_bot'
  );

-- ------------------------------------------------------------
-- 权限码
-- ------------------------------------------------------------

INSERT INTO admin_permission (code, description) VALUES
    ('cs:agent:manage', '租户智能客服管理（增删改查+绑定）'),
    ('cs:agent:read',   '租户智能客服只读')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r, admin_permission p
WHERE r.name = 'SUPER_ADMIN' AND r.tenant_id = 1
  AND p.code IN ('cs:agent:manage', 'cs:agent:read')
ON CONFLICT DO NOTHING;
