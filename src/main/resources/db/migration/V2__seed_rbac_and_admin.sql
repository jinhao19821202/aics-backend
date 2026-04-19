-- 权限码种子
INSERT INTO admin_permission (code, description) VALUES
    ('kb:document:read',    '知识文档只读'),
    ('kb:document:write',   '知识文档写'),
    ('kb:faq:read',         'FAQ 只读'),
    ('kb:faq:write',        'FAQ 写'),
    ('audit:session:read',  '会话审计只读'),
    ('audit:session:export','会话审计导出'),
    ('sensitive:read',      '敏感词只读'),
    ('sensitive:write',     '敏感词写'),
    ('stats:read',          '统计只读'),
    ('user:manage',         '用户管理'),
    ('group:manage',        '群映射管理'),
    ('handoff:manage',      '人工接管管理');

-- 角色种子
INSERT INTO admin_role (name, description, built_in) VALUES
    ('SUPER_ADMIN',  '超级管理员（全部权限）', TRUE),
    ('KB_ADMIN',     '知识库管理员',           TRUE),
    ('AUDIT_ADMIN',  '审计管理员',             TRUE),
    ('OPS_VIEWER',   '运营只读',               TRUE);

-- 角色-权限绑定
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r, admin_permission p WHERE r.name = 'SUPER_ADMIN';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r, admin_permission p
WHERE r.name = 'KB_ADMIN' AND p.code IN ('kb:document:read','kb:document:write','kb:faq:read','kb:faq:write','audit:session:read','stats:read');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r, admin_permission p
WHERE r.name = 'AUDIT_ADMIN' AND p.code IN ('audit:session:read','audit:session:export','sensitive:read','stats:read');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r, admin_permission p
WHERE r.name = 'OPS_VIEWER' AND p.code IN ('stats:read','audit:session:read','kb:document:read','kb:faq:read');

-- 初始超管账号（密码: Admin@123）
-- BCrypt hash generated for "Admin@123"
INSERT INTO admin_user (username, password_hash, display_name, email, enabled)
VALUES ('admin', '$2a$10$u3u3w9XjM86h4f/wGh7b.OyqIrq8hR8xRV9vvGVwMuxHl3HVtCTm.', '系统管理员', 'admin@example.com', TRUE);

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM admin_user u, admin_role r WHERE u.username = 'admin' AND r.name = 'SUPER_ADMIN';

-- 默认敏感词基线（示例，请管理员完善）
INSERT INTO sensitive_word (word, category, level, action) VALUES
    ('枪支',   'VIOLENCE', 'HIGH',   'BLOCK'),
    ('炸弹',   'VIOLENCE', 'HIGH',   'BLOCK'),
    ('傻逼',   'CUSTOM',   'MEDIUM', 'MASK'),
    ('妈的',   'CUSTOM',   'MEDIUM', 'MASK'),
    ('fuck',   'CUSTOM',   'LOW',    'ALERT'),
    ('手机号', 'PRIVACY',  'LOW',    'ALERT');

-- 初始化一条 group_session（demo group），避免 state 查询失败
-- 留空，M001 运行时 on-demand 创建
