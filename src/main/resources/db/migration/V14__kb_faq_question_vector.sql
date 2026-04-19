-- F001 FAQ 语义匹配：为 kb_faq.question 预计算 embedding 向量，
-- 命中顺序改为 exact → semantic(cosine ≥ 阈值) → keyword。
-- 向量以 JSONB 存储 List<Double>；dim 记录模型维度，用于检测模型变更后自动失效。

ALTER TABLE kb_faq
    ADD COLUMN question_vector JSONB,
    ADD COLUMN question_vector_dim INTEGER,
    ADD COLUMN question_vector_updated_at TIMESTAMPTZ;

COMMENT ON COLUMN kb_faq.question_vector IS '问题文本的 embedding（JSON 数组，float64）。与 question_vector_dim 一致时才参与匹配。';
COMMENT ON COLUMN kb_faq.question_vector_dim IS '向量维度；embedding 模型维度变更后旧向量失效，需 reindex。';
COMMENT ON COLUMN kb_faq.question_vector_updated_at IS '向量最近一次写入时间。';
