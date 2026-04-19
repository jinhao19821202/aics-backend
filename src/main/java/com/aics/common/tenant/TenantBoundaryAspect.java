package com.aics.common.tenant;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Aspect
@Component
public class TenantBoundaryAspect {

    @Around("@annotation(boundary)")
    public Object around(ProceedingJoinPoint pjp, TenantBoundary boundary) throws Throwable {
        Long resolved = switch (boundary.source()) {
            case ARGUMENT -> fromArg(pjp.getArgs(), boundary.argIndex());
            case KAFKA_HEADER -> fromKafka(pjp.getArgs(), boundary.headerName());
            case MANUAL -> null;
        };

        boolean setHere = false;
        if (resolved != null && !TenantContext.isSet()) {
            TenantContext.set(resolved);
            setHere = true;
        }

        try {
            return pjp.proceed();
        } finally {
            // MANUAL 模式下，方法内部自行 set，这里也一并清理，保证线程复用安全
            if (setHere || boundary.source() == TenantBoundary.Source.MANUAL) {
                TenantContext.clear();
            }
        }
    }

    private Long fromArg(Object[] args, int idx) {
        if (args == null || idx < 0 || idx >= args.length) return null;
        Object a = args[idx];
        if (a instanceof Long l) return l;
        if (a instanceof Number n) return n.longValue();
        return null;
    }

    private Long fromKafka(Object[] args, String header) {
        if (args == null) return null;
        for (Object a : args) {
            if (a instanceof ConsumerRecord<?, ?> rec) {
                Header h = rec.headers().lastHeader(header);
                if (h != null && h.value() != null) {
                    try {
                        return Long.parseLong(new String(h.value(), StandardCharsets.UTF_8));
                    } catch (NumberFormatException e) {
                        log.warn("invalid tenantId header in kafka record: {}", e.getMessage());
                    }
                }
                return null;
            }
        }
        return null;
    }
}
