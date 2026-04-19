package com.aics.m002_dialog.service;

import com.aics.m002_dialog.dto.RetrievalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * F003 兜底信心度判定。
 */
@Slf4j
@Service
public class ConfidenceJudge {

    public static final double LOW_CONFIDENCE_THRESHOLD = 0.6;

    private static final Pattern USER_HANDOFF = Pattern.compile(
            "转人工|人工客服|找客服|联系客服|我要投诉|真人");

    private static final Pattern LLM_HANDOFF_PHRASE = Pattern.compile(
            "抱歉|需要人工|无法回答");

    private final StringRedisTemplate redis;

    public ConfidenceJudge(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isExplicitUserRequest(String query) {
        return query != null && USER_HANDOFF.matcher(query).find();
    }

    public boolean isRagTooWeak(RetrievalResult rag) {
        return rag == null || !rag.isHasResult() || rag.getTopConfidence() < LOW_CONFIDENCE_THRESHOLD;
    }

    public boolean llmRepliesHandoffPhrase(String reply) {
        return reply != null && LLM_HANDOFF_PHRASE.matcher(reply).find() && reply.length() < 50;
    }

    /** 连续两次低信心 → 兜底。 */
    public boolean incrementLowConfidenceAndCheck(String groupId) {
        String key = "cs:lowconf:" + groupId;
        Long v = redis.opsForValue().increment(key);
        redis.expire(key, Duration.ofMinutes(10));
        return v != null && v >= 2;
    }

    public void resetLowConfidence(String groupId) {
        redis.delete("cs:lowconf:" + groupId);
    }
}
