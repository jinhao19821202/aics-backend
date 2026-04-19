package com.aics.infra.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * P003 F005：排序模型（Reranker）统一客户端。
 * 适配 DashScope 原生 rerank 接口与 OpenAI-Compatible `/v1/rerank`（Cohere / Jina / BGE 自部署）。
 * 调用方传入 query + documents（粗排后的候选），返回与原顺序一一对应的 score；失败抛异常，由 caller 回退。
 */
@Slf4j
@Component
public class RerankClient {

    private static final String DASHSCOPE_PATH = "/services/rerank/text-rerank/text-rerank";
    private static final String OPENAI_COMPAT_PATH = "/rerank";
    private static final String OPENAI_COMPAT_V1_PATH = "/v1/rerank";

    /** 按原 documents 顺序返回 score（缺失位为 null → caller 视为 rerank 未给出）。 */
    public List<Double> rerank(Request req) {
        if (req.documents == null || req.documents.isEmpty()) return List.of();
        long started = System.currentTimeMillis();
        try {
            List<Scored> raw;
            if ("dashscope".equalsIgnoreCase(req.provider)) {
                raw = callDashScope(req);
            } else {
                raw = callOpenAiCompatible(req);
            }
            // 按 index 回填到原顺序
            Double[] out = new Double[req.documents.size()];
            for (Scored s : raw) {
                if (s.index >= 0 && s.index < out.length) out[s.index] = s.score;
            }
            long elapsed = System.currentTimeMillis() - started;
            log.debug("rerank ok provider={} model={} docs={} elapsedMs={}",
                    req.provider, req.model, req.documents.size(), elapsed);
            List<Double> list = new ArrayList<>(out.length);
            for (Double d : out) list.add(d);
            return list;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - started;
            log.warn("rerank failed provider={} model={} elapsedMs={} err={}",
                    req.provider, req.model, elapsed, e.getMessage());
            throw new RerankException(e.getMessage(), e);
        }
    }

    private List<Scored> callDashScope(Request req) {
        WebClient wc = webClient(req);
        Map<String, Object> body = Map.of(
                "model", req.model,
                "input", Map.of("query", req.query, "documents", req.documents),
                "parameters", Map.of("top_n", Math.max(1, req.topN), "return_documents", false));
        JsonNode resp = wc.post()
                .uri(DASHSCOPE_PATH)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofMillis(req.timeoutMs));
        if (resp == null) throw new RerankException("empty response");
        JsonNode results = resp.path("output").path("results");
        if (!results.isArray()) throw new RerankException("invalid rerank resp: " + resp);
        List<Scored> out = new ArrayList<>();
        for (JsonNode n : results) {
            Scored s = new Scored();
            s.index = n.path("index").asInt(-1);
            s.score = n.path("relevance_score").asDouble();
            out.add(s);
        }
        return out;
    }

    private List<Scored> callOpenAiCompatible(Request req) {
        // Cohere / Jina / BGE 自部署 OpenAI 兼容协议
        WebClient wc = webClient(req);
        Map<String, Object> body = Map.of(
                "model", req.model,
                "query", req.query,
                "documents", req.documents,
                "top_n", Math.max(1, req.topN),
                "return_documents", false);
        String path = req.baseUrl != null && req.baseUrl.contains("/v1") ? OPENAI_COMPAT_PATH : OPENAI_COMPAT_V1_PATH;
        JsonNode resp = wc.post()
                .uri(path)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofMillis(req.timeoutMs));
        if (resp == null) throw new RerankException("empty response");
        JsonNode results = resp.path("results");
        if (!results.isArray()) throw new RerankException("invalid rerank resp: " + resp);
        List<Scored> out = new ArrayList<>();
        for (JsonNode n : results) {
            Scored s = new Scored();
            s.index = n.path("index").asInt(-1);
            s.score = n.path("relevance_score").asDouble(n.path("score").asDouble());
            out.add(s);
        }
        out.sort(Comparator.comparingInt(a -> a.index));
        return out;
    }

    private WebClient webClient(Request req) {
        String base = req.baseUrl;
        if ("dashscope".equalsIgnoreCase(req.provider) && (base == null || base.isBlank())) {
            base = "https://dashscope.aliyuncs.com/api/v1";
        }
        if (base == null || base.isBlank()) {
            throw new RerankException("baseUrl required for provider=" + req.provider);
        }
        return WebClient.builder()
                .baseUrl(base)
                .defaultHeader("Authorization", "Bearer " + req.apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public static class Request {
        public String provider;
        public String model;
        public String baseUrl;
        public String apiKey;
        public String query;
        public List<String> documents;
        public int topN;
        public int timeoutMs;
    }

    public static class Scored {
        public int index;
        public double score;
    }

    public static class RerankException extends RuntimeException {
        public RerankException(String msg) { super(msg); }
        public RerankException(String msg, Throwable t) { super(msg, t); }
    }
}
