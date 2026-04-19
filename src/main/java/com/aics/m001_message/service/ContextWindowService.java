package com.aics.m001_message.service;

import com.aics.common.JsonUtil;
import com.aics.common.tenant.TenantContext;
import com.aics.m001_message.domain.SessionMessage;
import com.aics.m001_message.domain.SessionMessageRepository;
import com.aics.m001_message.dto.ContextSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * F003 群级 10 轮滑动上下文窗口管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextWindowService {

    public static final int MAX_WINDOW = 10;
    public static final int MAX_TEXT_LEN = 2000;
    public static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final SessionMessageRepository sessionRepo;

    public static String windowKey(String groupId) {
        return "ctx:wecom:group:" + groupId;
    }

    /** 文本消息进入上下文窗口（图片/语音等不进） + 异步持久化。 */
    public void onTextMessage(String groupId, String userId, String userName,
                              String msgId, String text, boolean isBot) {
        if (text == null) return;
        String truncated = text.length() > MAX_TEXT_LEN ? text.substring(0, MAX_TEXT_LEN) : text;

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("msg_id", msgId);
        item.put("user_id", userId);
        item.put("user_name", userName);
        item.put("text", truncated);
        item.put("ts", System.currentTimeMillis() / 1000);
        item.put("is_bot", isBot);

        try {
            String key = windowKey(groupId);
            redis.opsForList().leftPush(key, JsonUtil.toJson(item));
            redis.opsForList().trim(key, 0, MAX_WINDOW - 1);
            redis.expire(key, TTL);
        } catch (Exception e) {
            log.warn("context redis write failed, degrade: {}", e.getMessage());
        }

        persistAsync(groupId, userId, userName, msgId, truncated, isBot);
    }

    private void persistAsync(String groupId, String userId, String userName, String msgId, String text, boolean isBot) {
        try {
            SessionMessage sm = new SessionMessage();
            sm.setTenantId(TenantContext.require());
            sm.setGroupId(groupId);
            sm.setMsgId(msgId);
            sm.setUserId(userId);
            sm.setUserName(userName);
            sm.setRole(isBot ? "bot" : "user");
            sm.setText(text);
            sm.setTokenCount(text.length() / 2);
            sessionRepo.save(sm);
        } catch (Exception e) {
            log.warn("persist session_message failed: {}", e.getMessage());
        }
    }

    /** 非文本消息：仅持久化，不入窗口。 */
    public void persistNonText(String groupId, String userId, String userName, String msgId, String type) {
        try {
            SessionMessage sm = new SessionMessage();
            sm.setTenantId(TenantContext.require());
            sm.setGroupId(groupId);
            sm.setMsgId(msgId);
            sm.setUserId(userId);
            sm.setUserName(userName);
            sm.setRole("user");
            sm.setText("[" + type + "]");
            sm.setTokenCount(0);
            sessionRepo.save(sm);
        } catch (Exception e) {
            log.warn("persist non-text failed: {}", e.getMessage());
        }
    }

    /** 被 @ 时取窗口快照：最多 10 条，按时间正序；**不含**当前触发消息。 */
    public List<ContextSnapshot.HistoryItem> snapshot(String groupId, String excludeMsgId) {
        try {
            List<String> raw = redis.opsForList().range(windowKey(groupId), 0, MAX_WINDOW - 1);
            if (raw == null) return List.of();
            List<ContextSnapshot.HistoryItem> out = new ArrayList<>();
            for (String s : raw) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = JsonUtil.fromJson(s, Map.class);
                    if (excludeMsgId != null && excludeMsgId.equals(m.get("msg_id"))) continue;
                    ContextSnapshot.HistoryItem h = new ContextSnapshot.HistoryItem(
                            String.valueOf(m.getOrDefault("user_name", m.getOrDefault("user_id", "user"))),
                            String.valueOf(m.getOrDefault("text", "")),
                            Boolean.TRUE.equals(m.get("is_bot")),
                            m.get("ts") == null ? 0L : ((Number) m.get("ts")).longValue()
                    );
                    out.add(h);
                } catch (Exception parseErr) {
                    log.warn("corrupt window entry, skipping: {}", parseErr.getMessage());
                }
            }
            // Redis LPUSH 存的是新→旧，逆序为时间正序
            Collections.reverse(out);
            return out;
        } catch (Exception e) {
            log.warn("snapshot failed, degrade no-context: {}", e.getMessage());
            return List.of();
        }
    }

    public void clear(String groupId) {
        redis.delete(windowKey(groupId));
    }
}
