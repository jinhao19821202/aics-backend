package com.aics.common.crypto;

import com.aics.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM 加解密租户敏感配置（tenant_llm_config.api_key_cipher、tenant_wecom_app.secret_cipher 等）。
 * 主密钥从 app.security.master-key 读取（base64 编码 32 字节）。
 * 加密输出格式：base64(iv(12B) || ciphertext || tag(16B))。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmSecretCipher {

    private static final String ALG = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final AppProperties props;
    private final Environment environment;
    private final SecureRandom random = new SecureRandom();
    private SecretKey key;

    @PostConstruct
    void init() {
        String raw = props.getSecurity() == null ? null : props.getSecurity().getMasterKey();
        if (raw == null || raw.isBlank()) {
            boolean devProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev");
            if (!devProfile) {
                throw new IllegalStateException(
                        "AICS_MASTER_KEY 未配置：非 dev profile 下必须提供稳定的 32 字节 base64 主密钥，否则进程重启后所有租户密文（LLM api_key / 企业微信 secret 等）将不可解密。"
                                + "请在环境变量中设置 AICS_MASTER_KEY=$(openssl rand -base64 32) 并妥善备份。");
            }
            log.warn("AICS_MASTER_KEY 未配置，LlmSecretCipher 将使用内存派生密钥（仅 dev profile 允许；重启后密文不可解密）");
            byte[] fallback = new byte[32];
            random.nextBytes(fallback);
            key = new SecretKeySpec(fallback, "AES");
            return;
        }
        byte[] bytes = Base64.getDecoder().decode(raw.trim());
        if (bytes.length != 32) {
            throw new IllegalStateException("AICS_MASTER_KEY 必须是 base64 编码的 32 字节，当前长度=" + bytes.length);
        }
        key = new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALG);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length);
            buf.put(iv).put(ct);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("encrypt failed", e);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null) return null;
        try {
            byte[] all = Base64.getDecoder().decode(cipherText);
            if (all.length <= IV_LEN + 16) {
                throw new IllegalArgumentException("cipher too short");
            }
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            byte[] ctTag = new byte[all.length - IV_LEN];
            System.arraycopy(all, IV_LEN, ctTag, 0, ctTag.length);
            Cipher cipher = Cipher.getInstance(ALG);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = cipher.doFinal(ctTag);
            return new String(pt, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("decrypt failed", e);
        }
    }

    public String tail(String plain) {
        if (plain == null || plain.length() < 4) return null;
        return plain.substring(plain.length() - 4);
    }
}
