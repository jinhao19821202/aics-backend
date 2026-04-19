package com.aics.m004_handoff.service;

import com.aics.common.tenant.TenantContext;
import com.aics.m004_handoff.domain.HandoffRecord;
import com.aics.m004_handoff.domain.HandoffRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/** 每 5 分钟扫一遍 handoff_record，补偿 Redis TTL 丢失导致的未关闭会话。*/
@Slf4j
@Service
@RequiredArgsConstructor
public class HandoffTimeoutSweeper {

    private final HandoffRecordRepository repo;
    private final HandoffService handoffService;
    private final StringRedisTemplate redis;

    @Scheduled(fixedDelay = 5 * 60_000, initialDelay = 60_000)
    public void sweep() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(30);
        List<HandoffRecord> stale = repo.findStale(threshold);
        if (stale.isEmpty()) return;

        for (HandoffRecord h : stale) {
            Long tid = h.getTenantId() != null ? h.getTenantId() : TenantContext.DEFAULT_TENANT_ID;
            String tk = GroupSessionService.timerKey(tid, h.getGroupId());
            if (Boolean.TRUE.equals(redis.hasKey(tk))) continue;
            TenantContext.set(tid);
            try {
                handoffService.close(h.getId(), "TIMEOUT");
            } catch (Exception e) {
                log.warn("sweep close failed id={}: {}", h.getId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }
}
