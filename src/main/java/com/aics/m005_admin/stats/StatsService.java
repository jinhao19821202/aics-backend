package com.aics.m005_admin.stats;

import com.aics.common.tenant.TenantContext;
import com.aics.m005_admin.audit.LlmInvocationRepository;
import com.aics.m005_admin.sensitive.SensitiveHitLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final LlmInvocationRepository llmRepo;
    private final SensitiveHitLogRepository hitRepo;

    public Map<String, Object> summary(int days) {
        Long tenantId = TenantContext.require();
        OffsetDateTime from = OffsetDateTime.now().minusDays(Math.max(1, days));
        OffsetDateTime to = OffsetDateTime.now();

        long total = llmRepo.countBetween(tenantId, from, to);
        long handoff = llmRepo.countHandoffBetween(tenantId, from, to);
        long selfResolved = Math.max(0, total - handoff);
        double avg = nz(llmRepo.avgLatencyBetween(tenantId, from, to));
        double p95 = nz(llmRepo.p95LatencyBetween(tenantId, from, to));
        Long tokensVal = llmRepo.totalTokensBetween(tenantId, from, to);
        long tokens = tokensVal == null ? 0 : tokensVal;
        long sensitiveHits = hitRepo.countByTenantIdAndCreatedAtGreaterThanEqual(tenantId, from);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("windowDays", days);
        r.put("totalConversations", total);
        r.put("selfResolved", selfResolved);
        r.put("handoffCount", handoff);
        r.put("resolveRate", total == 0 ? 0 : round2((double) selfResolved / total));
        r.put("avgLatencyMs", (long) avg);
        r.put("p95LatencyMs", (long) p95);
        r.put("totalTokens", tokens);
        r.put("sensitiveHits", sensitiveHits);
        return r;
    }

    /** 按天生成时间序列（days 天）。 */
    public List<Map<String, Object>> timeSeries(int days) {
        Long tenantId = TenantContext.require();
        List<Map<String, Object>> out = new ArrayList<>();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            OffsetDateTime from = d.atStartOfDay(zone).toOffsetDateTime();
            OffsetDateTime to = d.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
            long total = llmRepo.countBetween(tenantId, from, to);
            long handoff = llmRepo.countHandoffBetween(tenantId, from, to);
            double avg = nz(llmRepo.avgLatencyBetween(tenantId, from, to));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", d.toString());
            item.put("total", total);
            item.put("handoff", handoff);
            item.put("avgLatencyMs", (long) avg);
            out.add(item);
        }
        return out;
    }

    private double nz(Double v) { return v == null ? 0 : v; }
    private double round2(double v) { return Math.round(v * 1000.0) / 1000.0; }
}
