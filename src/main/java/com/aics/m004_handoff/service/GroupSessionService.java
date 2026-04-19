package com.aics.m004_handoff.service;

import com.aics.common.tenant.TenantContext;
import com.aics.m004_handoff.domain.GroupSession;
import com.aics.m004_handoff.domain.GroupSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupSessionService {

    public static final String STATE_BOT = "BOT_ACTIVE";
    public static final String STATE_HUMAN = "HUMAN_TAKEOVER";

    public static final Duration HANDOFF_TIMEOUT = Duration.ofMinutes(30);

    private final GroupSessionRepository repo;
    private final StringRedisTemplate redis;

    public String getState(String groupId) {
        Long tenantId = TenantContext.require();
        String cached = redis.opsForValue().get(stateKey(tenantId, groupId));
        if (cached != null) return cached;
        String state = repo.findByTenantIdAndGroupId(tenantId, groupId)
                .map(GroupSession::getState).orElse(STATE_BOT);
        redis.opsForValue().set(stateKey(tenantId, groupId), state, HANDOFF_TIMEOUT);
        return state;
    }

    @Transactional
    public void markHumanTakeover(String groupId, Long handoffId) {
        Long tenantId = TenantContext.require();
        GroupSession s = repo.findByTenantIdAndGroupId(tenantId, groupId).orElseGet(() -> {
            GroupSession n = new GroupSession();
            n.setTenantId(tenantId);
            n.setGroupId(groupId);
            return n;
        });
        s.setState(STATE_HUMAN);
        s.setHandoffId(handoffId);
        s.setLastActivityAt(OffsetDateTime.now());
        s.setUpdatedAt(OffsetDateTime.now());
        repo.save(s);
        redis.opsForValue().set(stateKey(tenantId, groupId), STATE_HUMAN, HANDOFF_TIMEOUT);
        redis.opsForValue().set(timerKey(tenantId, groupId), "1", HANDOFF_TIMEOUT);
    }

    @Transactional
    public void backToBot(String groupId) {
        Long tenantId = TenantContext.require();
        repo.findByTenantIdAndGroupId(tenantId, groupId).ifPresent(s -> {
            s.setState(STATE_BOT);
            s.setHandoffId(null);
            s.setUpdatedAt(OffsetDateTime.now());
            repo.save(s);
        });
        redis.delete(stateKey(tenantId, groupId));
        redis.delete(timerKey(tenantId, groupId));
        redis.delete("dedup:handoff:t" + tenantId + ":" + groupId);
    }

    public void refreshTimer(String groupId) {
        Long tenantId = TenantContext.require();
        String k = timerKey(tenantId, groupId);
        if (Boolean.TRUE.equals(redis.hasKey(k))) {
            redis.expire(k, HANDOFF_TIMEOUT);
        }
    }

    public static String stateKey(Long tenantId, String groupId) {
        return "session:state:t" + tenantId + ":" + groupId;
    }

    public static String timerKey(Long tenantId, String groupId) {
        return "handoff:timer:t" + tenantId + ":" + groupId;
    }
}
