package com.aics.common.tenant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记非 HTTP 入口方法（Kafka Consumer / @Scheduled / @Async 任务）：
 *  - 进入时设置 TenantContext（从参数 / 消息头解出 tenantId）
 *  - 方法结束 finally 清理
 *
 * 配合 {@link TenantBoundaryAspect} 使用。
 *
 * 用法示例：
 * <pre>
 *   @TenantBoundary(source = TenantBoundary.Source.KAFKA_HEADER, headerName = "tenantId")
 *   public void onMessage(ConsumerRecord&lt;String, String&gt; record) { ... }
 *
 *   @TenantBoundary(source = TenantBoundary.Source.ARGUMENT, argIndex = 0)
 *   public void processForTenant(Long tenantId, ...) { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TenantBoundary {

    enum Source {
        /** 从方法第 argIndex 个参数（必须是 Long）取 tenantId */
        ARGUMENT,
        /** 从 Kafka ConsumerRecord headers 读 headerName */
        KAFKA_HEADER,
        /** 手动在方法体内 TenantContext.set(...)，切面仅负责 finally 清理 */
        MANUAL
    }

    Source source() default Source.MANUAL;

    int argIndex() default 0;

    String headerName() default "tenantId";
}
