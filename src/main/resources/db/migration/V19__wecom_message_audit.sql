-- P005 F002：wecom_message 扩展"密文 + 验签状态 + 路由上下文"字段，支持管理后台消息审计。
--
-- 设计要点：
-- 1) 保留原 raw（明文 XML）不动；新增 encrypted_payload 存原始 <Encrypt> 密文，验签失败时为 NULL；
-- 2) msg_signature / timestamp_str / nonce 记录企微 GET 参数，便于线下重放解密复现；
--    timestamp 保持 VARCHAR（企微原参是字符串，避免时区歧义）；
-- 3) wecom_app_id 关联到 tenant_wecom_app；ON DELETE SET NULL 保证应用删除后历史消息仍可查；
-- 4) verify_status 只有 VERIFIED / UNKNOWN（历史数据）/ REJECTED 三值；当前简化方案下 REJECTED 不落库，
--    保留枚举值是为后续"阶段 A 先写密文"升级路径；
-- 5) 索引 (tenant_id, wecom_app_id, created_at DESC) 服务于管理后台"按应用+时间"主查询路径。

ALTER TABLE wecom_message
    ADD COLUMN encrypted_payload TEXT,
    ADD COLUMN msg_signature     VARCHAR(64),
    ADD COLUMN timestamp_str     VARCHAR(20),
    ADD COLUMN nonce             VARCHAR(64),
    ADD COLUMN wecom_app_id      BIGINT REFERENCES tenant_wecom_app(id) ON DELETE SET NULL,
    ADD COLUMN verify_status     VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'
        CHECK (verify_status IN ('UNKNOWN', 'VERIFIED', 'REJECTED'));

CREATE INDEX idx_wecom_msg_tenant_app_time
    ON wecom_message (tenant_id, wecom_app_id, created_at DESC);

COMMENT ON COLUMN wecom_message.encrypted_payload IS '企微 POST body 原始 <Encrypt> 密文（Base64），验签失败/历史数据为 NULL';
COMMENT ON COLUMN wecom_message.msg_signature     IS '企微 GET 参数 msg_signature（签名），用于线下重放复现';
COMMENT ON COLUMN wecom_message.timestamp_str     IS '企微 GET 参数 timestamp（保持字符串原样）';
COMMENT ON COLUMN wecom_message.nonce             IS '企微 GET 参数 nonce';
COMMENT ON COLUMN wecom_message.wecom_app_id      IS '路由到的 tenant_wecom_app.id；默认租户兼容路径可为 NULL';
COMMENT ON COLUMN wecom_message.verify_status     IS 'VERIFIED=签名校验通过; REJECTED=验签/解密失败; UNKNOWN=V19 之前的历史数据';

-- ------------------------------------------------------------
-- P005 F003 权限码：msg:wecom:read
-- 挂到默认租户 SUPER_ADMIN / AUDIT_ADMIN / OPS_VIEWER。
-- TENANT_OWNER 等运行时租户角色由 bootstrap 服务动态绑定（参考 V16 模式）。
-- ------------------------------------------------------------
INSERT INTO admin_permission (code, description) VALUES
    ('msg:wecom:read', '企微消息审计只读（密文 + 明文）')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r, admin_permission p
WHERE r.tenant_id = 1
  AND r.name IN ('SUPER_ADMIN', 'AUDIT_ADMIN', 'OPS_VIEWER')
  AND p.code = 'msg:wecom:read'
ON CONFLICT DO NOTHING;
