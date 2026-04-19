package com.aics.infra.dashscope;

/**
 * 单次 DashScope 调用用到的凭证 + 目标；由 LlmClientResolver 按租户解析。
 *  - apiKey：请求时写到 Authorization: Bearer
 *  - baseUrl：若为 null 使用全局默认
 *  - embeddingModel/chatModel：embed/chat 的实际 model（chat 可由调用者覆盖）
 *  - embeddingDim：向量维度（用于调用侧校验，不发给 DashScope）
 */
public record DashScopeCreds(
        String apiKey,
        String baseUrl,
        String embeddingModel,
        String chatModel,
        Integer embeddingDim
) {}
