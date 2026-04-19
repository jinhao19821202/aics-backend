-- ==========================================================
-- V6 P002 M1 RBAC 拆分系统角色 / 租户角色
-- admin_role 加 tenant_id（NULL 表示系统角色，只属于运营平台）
-- 原有业务角色归属默认租户 1
-- 追加运营平台相关权限码
-- ==========================================================

-- admin_role 加 tenant_id 列
ALTER TABLE admin_role ADD COLUMN tenant_id BIGINT;
-- 原有业务角色（built_in=TRUE 中的 KB_ADMIN / AUDIT_ADMIN 等）归默认租户 1
UPDATE admin_role SET tenant_id = 1 WHERE name IN ('SUPER_ADMIN', 'KB_ADMIN', 'AUDIT_ADMIN', 'OPS_VIEWER');
-- role name 在租户内唯一（不同租户可以有同名角色）
ALTER TABLE admin_role DROP CONSTRAINT admin_role_name_key;
CREATE UNIQUE INDEX uk_admin_role_tenant_name ON admin_role (COALESCE(tenant_id, 0), name);
CREATE INDEX idx_admin_role_tenant ON admin_role (tenant_id);

-- ------------------------------------------------------------
-- 新增运营侧权限码
-- ------------------------------------------------------------
INSERT INTO admin_permission (code, description) VALUES
    ('ops:tenant:read',     '运营查询租户'),
    ('ops:tenant:manage',   '运营管理租户(增/改/停用)'),
    ('ops:usage:read',      '运营查询全局用量'),
    ('ops:llm:inspect',     '运营查询 LLM 调用审计'),
    ('ops:audit:read',      '运营查询访问审计'),
    ('ops:user:manage',     '运营账号管理'),
    ('tenant:model:view',   '租户模型配置只读'),
    ('tenant:model:config', '租户模型配置读写'),
    ('tenant:kb:read',      '租户知识库只读（含召回调试）'),
    ('tenant:kb:manage',    '租户知识库读写')
ON CONFLICT (code) DO NOTHING;

-- ------------------------------------------------------------
-- 新增运营内置角色（tenant_id = NULL 系统角色）
-- ------------------------------------------------------------
INSERT INTO admin_role (name, description, built_in, tenant_id) VALUES
    ('OPS_SUPER_ADMIN', '运营超级管理员',  TRUE, NULL),
    ('OPS_ADMIN',       '运营管理员',      TRUE, NULL),
    ('OPS_VIEWER_SYS',  '运营只读（系统）', TRUE, NULL);

-- 运营超管拥有所有 ops:* 权限
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r, admin_permission p
WHERE r.name = 'OPS_SUPER_ADMIN' AND p.code LIKE 'ops:%'
ON CONFLICT DO NOTHING;

-- 运营管理员：除了 ops:user:manage 之外的 ops
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r, admin_permission p
WHERE r.name = 'OPS_ADMIN'
  AND p.code IN ('ops:tenant:read','ops:tenant:manage','ops:usage:read','ops:llm:inspect','ops:audit:read')
ON CONFLICT DO NOTHING;

-- 运营只读：仅 read 类
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r, admin_permission p
WHERE r.name = 'OPS_VIEWER_SYS'
  AND p.code IN ('ops:tenant:read','ops:usage:read','ops:llm:inspect','ops:audit:read')
ON CONFLICT DO NOTHING;

-- 给现有 SUPER_ADMIN（默认租户）追加新的租户级权限
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r, admin_permission p
WHERE r.name = 'SUPER_ADMIN' AND r.tenant_id = 1
  AND p.code IN ('tenant:model:view','tenant:model:config','tenant:kb:read','tenant:kb:manage')
ON CONFLICT DO NOTHING;

-- KB_ADMIN 追加 tenant:kb:*
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r, admin_permission p
WHERE r.name = 'KB_ADMIN' AND r.tenant_id = 1
  AND p.code IN ('tenant:kb:read','tenant:kb:manage','tenant:model:view')
ON CONFLICT DO NOTHING;
