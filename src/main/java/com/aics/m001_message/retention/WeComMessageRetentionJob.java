package com.aics.m001_message.retention;

import com.aics.config.AppProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * P005 F004：wecom_message 保留期清理。
 * 配置项 app.wecom.message-retention-days（默认 30）；0 或负值表示跳过清理。
 * 每日 03:10 执行；按 1000 条分批删除避免长事务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeComMessageRetentionJob {

    private static final int BATCH_SIZE = 1000;
    private static final int SAFETY_MAX_BATCHES = 1000; // 避免死循环；上限 100 万条

    private final AppProperties props;

    @PersistenceContext
    private EntityManager em;

    @Scheduled(cron = "0 10 3 * * *")
    public void run() {
        int days = props.getWecom().getMessageRetentionDays();
        if (days <= 0) {
            log.debug("wecom-message-retention: disabled (messageRetentionDays={})", days);
            return;
        }
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(days);
        long total = 0;
        for (int i = 0; i < SAFETY_MAX_BATCHES; i++) {
            int deleted = deleteBatch(cutoff);
            total += deleted;
            if (deleted < BATCH_SIZE) break;
        }
        if (total > 0) {
            log.info("wecom-message-retention: deleted {} rows older than {} days (cutoff={})", total, days, cutoff);
        } else {
            log.debug("wecom-message-retention: no rows older than {} days", days);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected int deleteBatch(OffsetDateTime cutoff) {
        // Postgres 支持 DELETE ... WHERE ctid IN (subquery)。这里用原生 SQL + LIMIT 子查询按批删，
        // 避免 JPA DELETE 不支持 LIMIT 的局限。
        return em.createNativeQuery(
                        "DELETE FROM wecom_message " +
                        "WHERE id IN (" +
                        "  SELECT id FROM wecom_message WHERE created_at < :cutoff LIMIT :batch" +
                        ")")
                .setParameter("cutoff", cutoff)
                .setParameter("batch", BATCH_SIZE)
                .executeUpdate();
    }
}
