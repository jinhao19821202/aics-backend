-- P003 F004：召回调试日志增加过滤参数 + Top 命中定位
-- 用于运营/租户回溯"这条查询当时是带哪些文档/标签过滤的""命中的第一条属于哪份文档/哪条 chunk"。

ALTER TABLE kb_playground_log
  ADD COLUMN IF NOT EXISTS filters      JSONB  NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN IF NOT EXISTS top_doc_id   BIGINT,
  ADD COLUMN IF NOT EXISTS top_chunk_id BIGINT,
  ADD COLUMN IF NOT EXISTS rerank_used  BOOLEAN NOT NULL DEFAULT FALSE;

-- 支持"某份文档最近的被召回记录"查询
CREATE INDEX IF NOT EXISTS idx_kb_playground_top_doc
  ON kb_playground_log (tenant_id, top_doc_id, created_at DESC)
  WHERE top_doc_id IS NOT NULL;
