-- ==========================================================
-- V7 P002 M1 默认租户 seed
-- 写入 id=1, code=default 的默认租户；admin_user 归属此租户
-- 企微 / DashScope 配置占位（启动时由 DefaultTenantBootstrap 用 app.wecom.* / app.dashscope.* 替换）
-- ==========================================================

-- 明确将默认租户 id 固定为 1；SERIAL 序列后续自动向后推进
INSERT INTO tenant (id, code, name, status, plan, milvus_collection, embedding_dim)
VALUES (1, 'default', '默认租户', 'active', 'enterprise', 'aics_kb_t1', 1024)
ON CONFLICT (id) DO NOTHING;

-- 推进序列到 1 之后（如果尚未推进）
SELECT setval(pg_get_serial_sequence('tenant','id'), GREATEST(1, (SELECT COALESCE(MAX(id), 1) FROM tenant)));

-- ------------------------------------------------------------
-- 企微应用占位行（token/aes_key/secret 启动时由 DefaultTenantBootstrap 填充）
-- 如果 V1 时还没有任何 app.wecom.* 配置，这行会在 bootstrap 里被 skip。
-- 这里先写 placeholder，启动后 update。
-- ------------------------------------------------------------
INSERT INTO tenant_wecom_app (tenant_id, corp_id, agent_id, token, aes_key_cipher, secret_cipher, bot_userid, api_base, enabled)
VALUES (1, '__PLACEHOLDER__', 0, '__PLACEHOLDER__', '__PLACEHOLDER__', '__PLACEHOLDER__', NULL, NULL, FALSE)
ON CONFLICT (corp_id, agent_id) DO NOTHING;

-- ------------------------------------------------------------
-- LLM 默认配置占位（同上，启动时由 DefaultTenantBootstrap 填充）
-- ------------------------------------------------------------
-- chat
INSERT INTO tenant_llm_config (tenant_id, provider, purpose, api_key_cipher, api_key_tail, base_url, model, params, is_default, enabled)
VALUES (1, 'dashscope', 'chat', '__PLACEHOLDER__', NULL, NULL, '__PLACEHOLDER__', '{}'::jsonb, FALSE, FALSE);

-- embedding
INSERT INTO tenant_llm_config (tenant_id, provider, purpose, api_key_cipher, api_key_tail, base_url, model, embedding_dim, params, is_default, enabled)
VALUES (1, 'dashscope', 'embedding', '__PLACEHOLDER__', NULL, NULL, '__PLACEHOLDER__', 1024, '{}'::jsonb, FALSE, FALSE);
