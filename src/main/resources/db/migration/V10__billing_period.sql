-- 月度结算周期：每月 1 号 02:00 由 BillingSettlementService 写入上一个月的汇总。
CREATE TABLE IF NOT EXISTS billing_period (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT        NOT NULL,
    period_year       INT           NOT NULL,
    period_month      INT           NOT NULL,
    total_calls       BIGINT        NOT NULL DEFAULT 0,
    prompt_tokens     BIGINT        NOT NULL DEFAULT 0,
    completion_tokens BIGINT        NOT NULL DEFAULT 0,
    total_tokens      BIGINT        NOT NULL DEFAULT 0,
    amount            NUMERIC(14,4) NOT NULL DEFAULT 0,
    currency          VARCHAR(8)    NOT NULL DEFAULT 'CNY',
    model_breakdown   JSONB,
    generated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    note              VARCHAR(255),
    CONSTRAINT uk_billing_period UNIQUE (tenant_id, period_year, period_month),
    CONSTRAINT ck_billing_month  CHECK (period_month BETWEEN 1 AND 12)
);

CREATE INDEX IF NOT EXISTS idx_billing_period_year_month ON billing_period (period_year, period_month);
CREATE INDEX IF NOT EXISTS idx_billing_period_tenant     ON billing_period (tenant_id, period_year DESC, period_month DESC);
