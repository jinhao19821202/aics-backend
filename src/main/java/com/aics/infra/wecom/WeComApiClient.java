package com.aics.infra.wecom;

import com.aics.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 企业微信主动发消息 + access_token 缓存。
 */
@Slf4j
@Component
public class WeComApiClient {

    private static final String TOKEN_KEY = "wecom:access_token";

    private final AppProperties props;
    private final WebClient wecomWebClient;
    private final StringRedisTemplate redis;

    public WeComApiClient(AppProperties props,
                          @Qualifier("wecomWebClient") WebClient wecomWebClient,
                          StringRedisTemplate redis) {
        this.props = props;
        this.wecomWebClient = wecomWebClient;
        this.redis = redis;
    }

    public String getAccessToken() {
        String cached = redis.opsForValue().get(TOKEN_KEY);
        if (cached != null) return cached;

        if (props.getWecom().getCorpId() == null || props.getWecom().getCorpId().isBlank()) {
            log.warn("wecom corpId/secret not configured; skip access_token");
            return "";
        }

        JsonNode resp = wecomWebClient.get()
                .uri(u -> u.path("/cgi-bin/gettoken")
                        .queryParam("corpid", props.getWecom().getCorpId())
                        .queryParam("corpsecret", props.getWecom().getSecret())
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(5));

        if (resp != null && resp.get("errcode").asInt() == 0) {
            String token = resp.get("access_token").asText();
            int expire = resp.get("expires_in").asInt(7200);
            redis.opsForValue().set(TOKEN_KEY, token, Duration.ofSeconds(Math.max(expire - 120, 60)));
            return token;
        }
        log.error("wecom gettoken failed: {}", resp);
        return "";
    }

    /** 发送文本消息到群聊（应用消息）。 */
    public void sendTextToGroup(String chatId, String text) {
        String token = getAccessToken();
        if (token == null || token.isBlank()) {
            log.warn("[dry-run] wecom no token, would send to chat={}: {}", chatId, text);
            return;
        }
        Map<String, Object> body = Map.of(
                "chatid", chatId,
                "msgtype", "text",
                "text", Map.of("content", text),
                "safe", 0
        );
        send("/cgi-bin/appchat/send", token, body);
    }

    /** 发送应用消息到群内成员（按 @ userid 列表）。 */
    public void sendTextToUsers(List<String> useridList, String text) {
        String token = getAccessToken();
        if (token == null || token.isBlank()) {
            log.warn("[dry-run] wecom no token, would send to users={}: {}", useridList, text);
            return;
        }
        Map<String, Object> body = Map.of(
                "touser", String.join("|", useridList),
                "msgtype", "text",
                "agentid", props.getWecom().getAgentId(),
                "text", Map.of("content", text)
        );
        send("/cgi-bin/message/send", token, body);
    }

    private void send(String path, String token, Map<String, Object> body) {
        JsonNode resp = wecomWebClient.post()
                .uri(u -> u.path(path).queryParam("access_token", token).build())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(5));
        if (resp == null || resp.get("errcode").asInt() != 0) {
            log.error("wecom send failed: path={}, resp={}", path, resp);
        }
    }
}
