-- P004-B F003：FAQ 分组
-- 1) 新建 kb_faq_group；租户内按 name 唯一；
-- 2) kb_faq 新增 group_id（先可空）+ 按租户种默认分组 + 回填；
-- 3) 用 CHECK(group_id IS NOT NULL) NOT VALID + VALIDATE CONSTRAINT 的分步方式避免大表锁（评审修订）；
-- 4) 权限码 kb:faq:group:manage 种到默认租户 SUPER_ADMIN。

CREATE TABLE kb_faq_group (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name        VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    sort_order  INTEGER NOT NULL DEFAULT 0,
    created_by  BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_kb_faq_group_tenant_name UNIQUE (tenant_id, name)
);
CREATE INDEX idx_kb_faq_group_tenant ON kb_faq_group(tenant_id, sort_order);

COMMENT ON TABLE kb_faq_group IS 'P004-B F003 FAQ 分组；租户内 name 唯一。';
COMMENT ON COLUMN kb_faq_group.sort_order IS '同租户内的展示顺序，升序；相同值按 id 升序。';

-- ------------------------------------------------------------
-- Step 1：为每个已有 FAQ 数据的租户种一个「默认分组」
-- ------------------------------------------------------------
INSERT INTO kb_faq_group (tenant_id, name, description, sort_order)
SELECT DISTINCT f.tenant_id, '默认分组', '升级自 v1.0 的未分组 FAQ（可改名 / 合并）', 0
FROM kb_faq f
WHERE NOT EXISTS (
    SELECT 1 FROM kb_faq_group g WHERE g.tenant_id = f.tenant_id AND g.name = '默认分组'
);

-- ------------------------------------------------------------
-- Step 2：加列（先允许 NULL），不阻塞；索引 CONCURRENTLY 由 repeatable migration 负责（这里普通索引即可）
-- ------------------------------------------------------------
ALTER TABLE kb_faq ADD COLUMN IF NOT EXISTS group_id BIGINT REFERENCES kb_faq_group(id) ON DELETE RESTRICT;
CREATE INDEX idx_kb_faq_group ON kb_faq(group_id);

-- ------------------------------------------------------------
-- Step 3：回填历史 FAQ 到各租户的「默认分组」
-- ------------------------------------------------------------
UPDATE kb_faq f
SET group_id = (
    SELECT g.id FROM kb_faq_group g
    WHERE g.tenant_id = f.tenant_id AND g.name = '默认分组'
    LIMIT 1
)
WHERE f.group_id IS NULL;

-- ------------------------------------------------------------
-- Step 4：分步 NOT NULL（避免全表锁）—— 加 NOT VALID 约束 → VALIDATE 只扫不锁
-- ------------------------------------------------------------
ALTER TABLE kb_faq
    ADD CONSTRAINT chk_kb_faq_group_id_not_null CHECK (group_id IS NOT NULL) NOT VALID;

ALTER TABLE kb_faq VALIDATE CONSTRAINT chk_kb_faq_group_id_not_null;

-- ------------------------------------------------------------
-- 跨租户守护：kb_faq.group_id 必须与自身同租户（trigger 补 FK 不能表达的约束）
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION kb_faq_group_tenant_check() RETURNS trigger AS $$
BEGIN
    IF NEW.group_id IS NOT NULL THEN
        IF NEW.tenant_id <> (SELECT tenant_id FROM kb_faq_group WHERE id = NEW.group_id) THEN
            RAISE EXCEPTION 'kb_faq.group_id 必须与自身 tenant_id 一致（跨租户引用被拒绝）';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_kb_faq_group_tenant ON kb_faq;
CREATE TRIGGER trg_kb_faq_group_tenant
    BEFORE INSERT OR UPDATE OF tenant_id, group_id ON kb_faq
    FOR EACH ROW EXECUTE FUNCTION kb_faq_group_tenant_check();

-- ------------------------------------------------------------
-- 权限码
-- ------------------------------------------------------------
INSERT INTO admin_permission (code, description) VALUES
    ('kb:faq:group:manage', '租户 FAQ 分组管理')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r, admin_permission p
WHERE r.name = 'SUPER_ADMIN' AND r.tenant_id = 1
  AND p.code IN ('kb:faq:group:manage')
ON CONFLICT DO NOTHING;
