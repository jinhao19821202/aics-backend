-- P003 F005：tenant_llm_config.purpose 扩展 rerank。
-- 原表 V4 未定义 CHECK 约束，直接新增约束即可（先清理历史脏数据的防御性语句可留空）。

-- 先按存在判断删除可能的旧约束（跨环境兼容）
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'tenant_llm_config_purpose_check'
    ) THEN
        ALTER TABLE tenant_llm_config DROP CONSTRAINT tenant_llm_config_purpose_check;
    END IF;
END $$;

ALTER TABLE tenant_llm_config
  ADD CONSTRAINT tenant_llm_config_purpose_check
  CHECK (purpose IN ('chat','embedding','rerank'));
