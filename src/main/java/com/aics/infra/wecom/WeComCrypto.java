package com.aics.infra.wecom;

import com.aics.common.BizException;
import com.aics.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * 企业微信回调加解密工具（参考官方 WXBizMsgCrypt 实现）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeComCrypto {

    private final AppProperties props;

    public String signature(String timestamp, String nonce, String encrypted) {
        return signature(props.getWecom().getToken(), timestamp, nonce, encrypted);
    }

    public String signature(String token, String timestamp, String nonce, String encrypted) {
        String[] arr = {token, timestamp, nonce, encrypted};
        Arrays.sort(arr);
        StringBuilder sb = new StringBuilder();
        for (String s : arr) sb.append(s);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (Exception e) {
            throw new BizException("signature error");
        }
    }

    public void verify(String timestamp, String nonce, String encrypted, String givenSig) {
        verify(props.getWecom().getToken(), timestamp, nonce, encrypted, givenSig);
    }

    public void verify(String token, String timestamp, String nonce, String encrypted, String givenSig) {
        if (!signature(token, timestamp, nonce, encrypted).equals(givenSig)) {
            throw new BizException(401, "invalid wecom signature");
        }
    }

    /**
     * 解密返回 [plainXml, receivedCorpId]，调用方需校验 corpId。
     */
    public DecryptResult decrypt(String encrypted) {
        return decrypt(props.getWecom().getAesKey(), encrypted);
    }

    public DecryptResult decrypt(String aesKeyB64, String encrypted) {
        try {
            byte[] aesKey = Base64.getDecoder().decode(aesKeyB64 + "=");
            byte[] data = Base64.getDecoder().decode(encrypted);

            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);

            byte[] original = cipher.doFinal(data);
            byte[] unpadded = pkcs7Unpad(original);

            // layout: 16 bytes random | 4 bytes msg_len (big endian) | msg | corpid
            ByteBuffer buf = ByteBuffer.wrap(unpadded, 16, 4);
            int msgLen = buf.getInt();

            String xml = new String(unpadded, 20, msgLen, StandardCharsets.UTF_8);
            String corpId = new String(unpadded, 20 + msgLen, unpadded.length - 20 - msgLen, StandardCharsets.UTF_8);
            return new DecryptResult(xml, corpId);
        } catch (Exception e) {
            log.error("wecom decrypt failed", e);
            throw new BizException(500, "decrypt failed");
        }
    }

    private byte[] pkcs7Unpad(byte[] buf) {
        int pad = buf[buf.length - 1] & 0xff;
        if (pad < 1 || pad > 32) pad = 0;
        return Arrays.copyOfRange(buf, 0, buf.length - pad);
    }

    public record DecryptResult(String plainXml, String receivedCorpId) {}
}
