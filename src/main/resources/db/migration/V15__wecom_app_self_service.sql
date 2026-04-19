-- P004-A F001 企微应用自助管理：
-- 1) tenant_wecom_app 增加显示名、连通性状态、首次验签成功时间、最近测试记录、反向 Agent 绑定列。
-- 2) cs_agent_id 本期先加列 + 索引；FK + 唯一索引延迟到 V16（tenant_cs_agent 建表后）添加，避免顺序依赖。
-- 3) 存量记录 seed 一个默认 name，避免 UI 列表空白。

ALTER TABLE tenant_wecom_app
    ADD COLUMN name          VARCHAR(64),
    ADD COLUMN status        VARCHAR(16) NOT NULL DEFAULT 'NOT_VERIFIED',
    ADD COLUMN verified_at   TIMESTAMPTZ,
    ADD COLUMN last_test_at  TIMESTAMPTZ,
    ADD COLUMN last_test_ok  BOOLEAN,
    ADD COLUMN last_test_msg TEXT,
    ADD COLUMN cs_agent_id   BIGINT;

CREATE INDEX idx_wecom_app_cs_agent ON tenant_wecom_app(cs_agent_id);

COMMENT ON COLUMN tenant_wecom_app.name IS 'F001 租户内展示名；管理员多应用时区分用。';
COMMENT ON COLUMN tenant_wecom_app.status IS 'F001 连通性状态：NOT_VERIFIED / VERIFIED / FAILED；首次企微回调验签通过 => VERIFIED。';
COMMENT ON COLUMN tenant_wecom_app.verified_at IS 'F001 首次验签成功时间。';
COMMENT ON COLUMN tenant_wecom_app.last_test_at IS 'F001 最近一次「连通性测试」按钮点击时间。';
COMMENT ON COLUMN tenant_wecom_app.last_test_ok IS 'F001 最近一次测试是否成功。';
COMMENT ON COLUMN tenant_wecom_app.last_test_msg IS 'F001 最近一次测试的 errmsg（失败时记录，成功时为 null）。';
COMMENT ON COLUMN tenant_wecom_app.cs_agent_id IS 'F002 反向绑定：该应用挂到哪个智能客服（tenant_cs_agent.id）。V16 加 FK + UNIQUE(cs_agent_id) WHERE NOT NULL。';

-- 存量记录默认名
UPDATE tenant_wecom_app SET name = '默认应用' WHERE name IS NULL;

-- 加 NOT NULL 收紧：上一步 seed 之后数据已完整
ALTER TABLE tenant_wecom_app ALTER COLUMN name SET NOT NULL;

-- F001 权限码
INSERT INTO admin_permission (code, description) VALUES
    ('wecom:app:manage', '租户企微应用自助管理')
ON CONFLICT (code) DO NOTHING;

-- 默认租户的 SUPER_ADMIN 角色授权（TENANT_OWNER 由租户创建时动态绑定，此处仅做默认租户 bootstrap）
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r, admin_permission p
WHERE r.name = 'SUPER_ADMIN' AND r.tenant_id = 1
  AND p.code = 'wecom:app:manage'
ON CONFLICT DO NOTHING;
