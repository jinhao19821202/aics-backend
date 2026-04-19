-- 修正 V2 种子里的 BCrypt 哈希（与 "Admin@123" 不匹配）
-- 新哈希由 htpasswd -bnBC 10 "" "Admin@123" 生成，前缀 $2y 替换为 $2a
UPDATE admin_user
SET password_hash = '$2a$10$ajpHv0jXmkCUPfwJoqgsteRbA1YP6dlm8k4XY1srwbTEP2r7ztd6y'
WHERE username = 'admin';
