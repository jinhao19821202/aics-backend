package com.aics.m001_message.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redis;

    /** true = 首次（应该处理）；false = 重放（已处理过）。*/
    public boolean firstSeen(String msgId) {
        String key = "dedup:msg:" + msgId;
        Boolean r = redis.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(10));
        return Boolean.TRUE.equals(r);
    }
}
