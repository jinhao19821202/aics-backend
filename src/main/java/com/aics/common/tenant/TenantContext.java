package com.aics.common.tenant;

import org.slf4j.MDC;

/**
 * 租户上下文 ThreadLocal。
 *
 * 所有入口（HTTP Controller / Kafka Consumer / @Scheduled / @Async）
 * 必须在 finally 中调用 {@link #clear()}，避免线程复用污染。
 */
public final class TenantContext {

    public static final String MDC_KEY = "t";
    public static final Long DEFAULT_TENANT_ID = 1L;

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long tenantId) {
        if (tenantId == null) {
            clear();
            return;
        }
        CURRENT.set(tenantId);
        MDC.put(MDC_KEY, String.valueOf(tenantId));
    }

    /** 当前租户 ID；未设置时抛异常，调用方保证上下文已初始化。 */
    public static Long require() {
        Long id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException("TenantContext not set; request outside tenant boundary?");
        }
        return id;
    }

    /** 当前租户 ID；未设置返回 null（仅运营 / 系统任务允许）。 */
    public static Long currentOrNull() {
        return CURRENT.get();
    }

    public static boolean isSet() {
        return CURRENT.get() != null;
    }

    public static void clear() {
        CURRENT.remove();
        MDC.remove(MDC_KEY);
    }
}
