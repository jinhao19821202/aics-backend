-- P004-B F004：智能客服（CS Agent）内容归属
-- 1) 两张映射表：Agent ↔ FAQ Group、Agent ↔ Document；未配置任何映射时等价于"该 Agent 能看到全部"（兼容语义）；
-- 2) 跨租户守护 trigger：禁止 Agent 映射到不同 tenant 的 Group / Document；
-- 3) 权限码 cs:agent:content:manage（隐含在 cs:agent:manage 下）。

-- ------------------------------------------------------------
-- Agent ↔ FAQ Group
-- ------------------------------------------------------------
CREATE TABLE cs_agent_faq_group_mapping (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    cs_agent_id     BIGINT NOT NULL REFERENCES tenant_cs_agent(id) ON DELETE CASCADE,
    faq_group_id    BIGINT NOT NULL REFERENCES kb_faq_group(id) ON DELETE CASCADE,
    created_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_cs_agent_faq_group UNIQUE (cs_agent_id, faq_group_id)
);
CREATE INDEX idx_cs_agent_faq_group_agent ON cs_agent_faq_group_mapping(cs_agent_id);
CREATE INDEX idx_cs_agent_faq_group_group ON cs_agent_faq_group_mapping(faq_group_id);

COMMENT ON TABLE cs_agent_faq_group_mapping IS 'P004-B F004 Agent 能看到的 FAQ 分组白名单；无映射 = 无限制。';

-- ------------------------------------------------------------
-- Agent ↔ Document
-- ------------------------------------------------------------
CREATE TABLE cs_agent_kb_document_mapping (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    cs_agent_id     BIGINT NOT NULL REFERENCES tenant_cs_agent(id) ON DELETE CASCADE,
    kb_document_id  BIGINT NOT NULL REFERENCES kb_document(id) ON DELETE CASCADE,
    created_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_cs_agent_kb_document UNIQUE (cs_agent_id, kb_document_id)
);
CREATE INDEX idx_cs_agent_kb_document_agent ON cs_agent_kb_document_mapping(cs_agent_id);
CREATE INDEX idx_cs_agent_kb_document_doc ON cs_agent_kb_document_mapping(kb_document_id);

COMMENT ON TABLE cs_agent_kb_document_mapping IS 'P004-B F004 Agent 能看到的文档白名单；无映射 = 无限制。';

-- ------------------------------------------------------------
-- 跨租户 guardrail trigger（FK 表达不了「三张表 tenant_id 必须一致」）
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION cs_agent_mapping_tenant_check() RETURNS trigger AS $$
DECLARE
    agent_tenant BIGINT;
    target_tenant BIGINT;
BEGIN
    SELECT tenant_id INTO agent_tenant FROM tenant_cs_agent WHERE id = NEW.cs_agent_id;
    IF agent_tenant IS NULL THEN
        RAISE EXCEPTION 'cs_agent_id=% 不存在', NEW.cs_agent_id;
    END IF;

    IF TG_TABLE_NAME = 'cs_agent_faq_group_mapping' THEN
        SELECT tenant_id INTO target_tenant FROM kb_faq_group WHERE id = NEW.faq_group_id;
    ELSIF TG_TABLE_NAME = 'cs_agent_kb_document_mapping' THEN
        SELECT tenant_id INTO target_tenant FROM kb_document WHERE id = NEW.kb_document_id;
    ELSE
        RETURN NEW;
    END IF;

    IF target_tenant IS NULL THEN
        RAISE EXCEPTION '被引用对象不存在（跨租户或已删除）';
    END IF;

    IF agent_tenant <> target_tenant OR NEW.tenant_id <> agent_tenant THEN
        RAISE EXCEPTION 'Agent/Group/Document/tenant_id 必须一致（跨租户引用被拒绝）: agent.tenant=%, target.tenant=%, row.tenant=%',
            agent_tenant, target_tenant, NEW.tenant_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_cs_agent_faq_group_tenant ON cs_agent_faq_group_mapping;
CREATE TRIGGER trg_cs_agent_faq_group_tenant
    BEFORE INSERT OR UPDATE ON cs_agent_faq_group_mapping
    FOR EACH ROW EXECUTE FUNCTION cs_agent_mapping_tenant_check();

DROP TRIGGER IF EXISTS trg_cs_agent_kb_document_tenant ON cs_agent_kb_document_mapping;
CREATE TRIGGER trg_cs_agent_kb_document_tenant
    BEFORE INSERT OR UPDATE ON cs_agent_kb_document_mapping
    FOR EACH ROW EXECUTE FUNCTION cs_agent_mapping_tenant_check();

-- ------------------------------------------------------------
-- 权限码：content 管理沿用 cs:agent:manage（不单拆细粒度，减少 UI 复杂度）
-- 仅在 admin_permission 留一条记录方便审计显示，无需落 role_permission。
-- ------------------------------------------------------------
INSERT INTO admin_permission (code, description) VALUES
    ('cs:agent:content:manage', '智能客服内容归属管理（FAQ 分组 / 文档映射）—— 绑定于 cs:agent:manage')
ON CONFLICT (code) DO NOTHING;
