package com.aics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String companyName;
    private String botName;
    /** P004-A F001：对外回调基础 URL（无 /api/wecom/callback 后缀），例如 https://callback.example.com。前端按 tenantCode 拼接展示。 */
    private String publicBaseUrl;

    private WeCom wecom = new WeCom();
    private DashScope dashscope = new DashScope();
    private Milvus milvus = new Milvus();
    private MinIO minio = new MinIO();
    private Jwt jwt = new Jwt();
    private KafkaTopics kafkaTopics = new KafkaTopics();
    private Security security = new Security();

    @Data
    public static class WeCom {
        private String corpId;
        private Integer agentId;
        private String token;
        private String aesKey;
        private String botUserid;
        private String secret;
        private String apiBase;
        /** P005 F004：wecom_message 保留天数；0 或负值 = 永不清理。 */
        private int messageRetentionDays = 30;
    }

    @Data
    public static class DashScope {
        private String apiKey;
        private String baseUrl;
        private String chatModel;
        private String largeModel;
        private String liteModel;
        private String embeddingModel;
        private int embeddingDim = 1024;
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 12000;
        private int streamFallbackTimeoutMs = 4000;
    }

    @Data
    public static class Milvus {
        private String host;
        private int port;
        private String collection;
    }

    @Data
    public static class MinIO {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
    }

    @Data
    public static class Jwt {
        private String secret;
        private int expireHours = 8;
        private int refreshDays = 7;
    }

    @Data
    public static class KafkaTopics {
        private String inbound;
        private String inboundDlq;
        private String triggered;
        private String handoff;
    }

    @Data
    public static class Security {
        /** base64 编码的 32 字节 AES-256 主密钥，用于加解密租户敏感配置（api key / wecom secret）。*/
        private String masterKey;
    }
}
