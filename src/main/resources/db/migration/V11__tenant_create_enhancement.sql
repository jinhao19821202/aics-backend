-- P003 F001/F002：新建租户 / 租户维度登录 的数据结构补齐
-- 注意：此迁移仅补列与种子权限，不修改现存行；status 字段保持 VARCHAR，新增枚举值 'trial' 由应用层约束。

ALTER TABLE tenant
    ADD COLUMN IF NOT EXISTS contact_email VARCHAR(128),
    ADD COLUMN IF NOT EXISTS trial_ends_at TIMESTAMPTZ;

ALTER TABLE admin_user
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- 跨租户 username 查询加速：登录时需要按 username 做全局歧义检测（count by username）
CREATE INDEX IF NOT EXISTS idx_admin_user_username ON admin_user (username);

-- 新增 TENANT_OWNER 相关内置权限码（角色 TENANT_OWNER 由服务在创建租户时动态绑定全部 tenant:* 权限）
INSERT INTO admin_permission (code, description)
VALUES ('tenant:owner', '租户 Owner（拥有本租户全部管理权限）')
ON CONFLICT (code) DO NOTHING;
