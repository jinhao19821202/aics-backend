package com.aics.infra.dashscope;

import com.aics.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

/**
 * DashScope (通义千问) HTTP 客户端：接收 {@link DashScopeCreds} 以支持多租户动态 apiKey / baseUrl。
 * 对外单例，熔断 / 重试注解保持在本类；LlmClientResolver 传入 tenant-specific creds 调用。
 */
@Slf4j
@Component
public class DashScopeClient {

    @SuppressWarnings("unused")
    private static final ObjectMapper OM = new ObjectMapper();
    private static final String PATH_CHAT = "/services/aigc/text-generation/generation";
    private static final String PATH_EMBED = "/services/embeddings/text-embedding/text-embedding";

    private final AppProperties props;
    private final WebClient defaultClient;

    public DashScopeClient(AppProperties props,
                           @Qualifier("dashScopeWebClient") WebClient client) {
        this.props = props;
        this.defaultClient = client;
    }

    public static class ChatResult {
        public String text;
        public int promptTokens;
        public int completionTokens;
        public long latencyMs;
        public String model;
    }

    // ----------- 新 API：带 creds -----------

    @CircuitBreaker(name = "llmQwen", fallbackMethod = "chatFallback")
    public ChatResult chat(DashScopeCreds creds, String model, String systemPrompt, String userPrompt,
                           double temperature, int maxTokens) {
        long started = System.currentTimeMillis();
        String actualModel = model != null && !model.isBlank() ? model
                : (creds != null && creds.chatModel() != null ? creds.chatModel()
                : props.getDashscope().getChatModel());

        Map<String, Object> input = Map.of(
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));

        Map<String, Object> parameters = Map.of(
                "temperature", temperature,
                "top_p", 0.8,
                "max_tokens", maxTokens,
                "result_format", "message");

        Map<String, Object> body = Map.of(
                "model", actualModel,
                "input", input,
                "parameters", parameters);

        try {
            JsonNode resp = clientFor(creds).post()
                    .uri(PATH_CHAT)
                    .headers(h -> applyAuth(h, creds))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofMillis(props.getDashscope().getReadTimeoutMs() + 1000));

            ChatResult r = new ChatResult();
            r.latencyMs = System.currentTimeMillis() - started;
            r.model = actualModel;
            if (resp == null || resp.get("output") == null) {
                throw new RuntimeException("empty response");
            }
            JsonNode output = resp.get("output");
            JsonNode choices = output.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                r.text = choices.get(0).path("message").path("content").asText();
            } else {
                r.text = output.path("text").asText("");
            }
            JsonNode usage = resp.get("usage");
            if (usage != null) {
                r.promptTokens = usage.path("input_tokens").asInt();
                r.completionTokens = usage.path("output_tokens").asInt();
            }
            return r;
        } catch (Exception e) {
            log.warn("qwen chat failed: {}", e.getMessage());
            throw e;
        }
    }

    @SuppressWarnings("unused")
    private ChatResult chatFallback(DashScopeCreds creds, String model, String sys, String user,
                                    double t, int mx, Throwable ex) {
        log.warn("qwen circuit/fallback, reason={}", ex.getMessage());
        ChatResult r = new ChatResult();
        r.text = null;
        r.model = model;
        return r;
    }

    @Retry(name = "dashScopeEmbedding")
    public List<float[]> embed(DashScopeCreds creds, List<String> texts) {
        if (texts.isEmpty()) return Collections.emptyList();
        String model = creds != null && creds.embeddingModel() != null
                ? creds.embeddingModel() : props.getDashscope().getEmbeddingModel();

        Map<String, Object> body = Map.of(
                "model", model,
                "input", Map.of("texts", texts),
                "parameters", Map.of("text_type", "document"));

        JsonNode resp = clientFor(creds).post()
                .uri(PATH_EMBED)
                .headers(h -> applyAuth(h, creds))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorResume(ex -> {
                    log.error("embedding request failed", ex);
                    return Mono.empty();
                })
                .block(Duration.ofSeconds(10));

        List<float[]> out = new ArrayList<>();
        if (resp == null) throw new RuntimeException("embedding empty resp");
        JsonNode embs = resp.path("output").path("embeddings");
        for (JsonNode n : embs) {
            JsonNode arr = n.path("embedding");
            float[] v = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++) v[i] = (float) arr.get(i).asDouble();
            out.add(l2Normalize(v));
        }
        return out;
    }

    public float[] embedOne(DashScopeCreds creds, String text) {
        List<float[]> out = embed(creds, List.of(text));
        if (out.isEmpty()) throw new RuntimeException("empty embedding");
        return out.get(0);
    }

    // ----------- 兼容 API：使用 app.dashscope.* 默认凭证 -----------

    public ChatResult chat(String model, String sys, String user, double temperature, int maxTokens) {
        return chat(null, model, sys, user, temperature, maxTokens);
    }

    public List<float[]> embed(List<String> texts) {
        return embed(null, texts);
    }

    public float[] embedOne(String text) {
        return embedOne(null, text);
    }

    // ----------- 内部工具 -----------

    private WebClient clientFor(DashScopeCreds creds) {
        if (creds == null || creds.baseUrl() == null || creds.baseUrl().isBlank()) {
            return defaultClient;
        }
        return defaultClient.mutate().baseUrl(creds.baseUrl()).build();
    }

    private void applyAuth(org.springframework.http.HttpHeaders h, DashScopeCreds creds) {
        if (creds != null && creds.apiKey() != null && !creds.apiKey().isBlank()) {
            h.set("Authorization", "Bearer " + creds.apiKey());
        }
    }

    static float[] l2Normalize(float[] v) {
        double s = 0;
        for (float x : v) s += x * x;
        double n = Math.sqrt(s);
        if (n == 0) return v;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / n);
        return out;
    }
}
