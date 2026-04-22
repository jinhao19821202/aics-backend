package com.aics.m001_message.controller;

import com.aics.common.BizException;
import com.aics.common.tenant.TenantContext;
import com.aics.config.AppProperties;
import com.aics.infra.wecom.WeComCrypto;
import com.aics.infra.wecom.WeComMessageParser;
import com.aics.m001_message.domain.WeComMessage;
import com.aics.m001_message.domain.WeComMessageRepository;
import com.aics.m001_message.dto.InboundEnvelope;
import com.aics.m001_message.logging.WeComCallbackLogger;
import com.aics.m001_message.logging.WeComCallbackLogger.Context;
import com.aics.m001_message.service.IdempotencyService;
import com.aics.m005_admin.tenant.TenantWecomAppResolver;
import com.aics.m005_admin.wecom.TenantWecomAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * F001 企业微信回调接入（多租户）：
 * 路径 /wecom/callback/{tenantCode} — 通过 tenantCode 定位 tenant_wecom_app 后
 * 取租户级 token/aes_key/secret 解密；不再读 app.wecom.*（仅作默认租户兜底）。
 * 路径 /wecom/callback（保留兼容）— 使用 DEFAULT_TENANT + AppProperties。
 * 必须 ≤ 500ms，所有业务在 Worker 异步处理。
 *
 * P005 F001：成功 / 失败 / 忽略路径全部走 WeComCallbackLogger 打结构化日志，
 * 便于线上 grep tenantId/wecomAppId/msgId/chatId 排障。
 * P005 F002：handleDecrypted 落库同时写入密文字段（encrypted_payload / msg_signature /
 * timestamp_str / nonce / wecom_app_id / verify_status）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/wecom/callback")
public class WeComCallbackController {

    private final WeComCrypto crypto;
    private final WeComMessageParser parser;
    private final IdempotencyService idem;
    private final WeComMessageRepository msgRepo;
    private final KafkaTemplate<String, Object> kafka;
    private final AppProperties props;
    private final TenantWecomAppResolver tenantResolver;
    private final WeComCallbackLogger callbackLogger;
    /** P004-A F001：用 ObjectProvider 避免与 TenantWecomAppService 之间的循环依赖初始化问题。 */
    private final ObjectProvider<TenantWecomAppService> wecomAppServiceProvider;

    // ---- tenantCode 多租户入口 ----

    @GetMapping(value = "/{tenantCode}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verifyTenant(@PathVariable String tenantCode,
                                               @RequestParam("msg_signature") String sig,
                                               @RequestParam String timestamp,
                                               @RequestParam String nonce,
                                               @RequestParam String echostr) {
        long t0 = System.currentTimeMillis();
        TenantWecomAppResolver.Resolved r;
        try {
            r = tenantResolver.resolveByTenantCode(tenantCode);
        } catch (BizException e) {
            callbackLogger.logReject(
                    Context.ofVerify(null, null, tenantCode, null),
                    e.getMessage(), System.currentTimeMillis() - t0);
            throw e;
        }
        TenantContext.set(r.tenantId());
        Context ctx = Context.ofVerify(r.tenantId(), r.app().getId(), tenantCode, r.app().getCorpId());
        String echo;
        try {
            echo = doVerify(r, sig, timestamp, nonce, echostr);
        } catch (BizException e) {
            callbackLogger.logReject(ctx, e.getMessage(), System.currentTimeMillis() - t0);
            throw e;
        }
        // P004-A F001：首次验签通过 → 写 status=VERIFIED
        TenantWecomAppService svc = wecomAppServiceProvider.getIfAvailable();
        if (svc != null) {
            try {
                svc.markVerified(r.app().getId());
            } catch (Exception e) {
                log.warn("markVerified failed for wecomAppId={}: {}", r.app().getId(), e.getMessage());
            }
        }
        callbackLogger.logOk(ctx, System.currentTimeMillis() - t0);
        return ResponseEntity.ok(echo);
    }

    @PostMapping(value = "/{tenantCode}",
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE},
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> receiveTenant(@PathVariable String tenantCode,
                                                @RequestParam("msg_signature") String sig,
                                                @RequestParam String timestamp,
                                                @RequestParam String nonce,
                                                @RequestBody String body) {
        long t0 = System.currentTimeMillis();
        TenantWecomAppResolver.Resolved r;
        try {
            r = tenantResolver.resolveByTenantCode(tenantCode);
        } catch (BizException e) {
            callbackLogger.logReject(
                    Context.ofReceive(null, null, tenantCode, null),
                    e.getMessage(), System.currentTimeMillis() - t0);
            throw e;
        }
        TenantContext.set(r.tenantId());
        Context ctx = Context.ofReceive(r.tenantId(), r.app().getId(), tenantCode, r.app().getCorpId());
        try {
            String ack = doReceive(ctx, r, sig, timestamp, nonce, body, t0);
            return ResponseEntity.ok(ack);
        } catch (BizException e) {
            callbackLogger.logReject(ctx, e.getMessage(), System.currentTimeMillis() - t0);
            throw e;
        }
    }

    // ---- 默认租户兼容入口（M1 过渡）----

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(@RequestParam("msg_signature") String sig,
                                         @RequestParam String timestamp,
                                         @RequestParam String nonce,
                                         @RequestParam String echostr) {
        long t0 = System.currentTimeMillis();
        TenantContext.set(TenantContext.DEFAULT_TENANT_ID);
        String expected = props.getWecom().getCorpId();
        Context ctx = Context.ofVerify(TenantContext.DEFAULT_TENANT_ID, null, "default", expected);
        try {
            crypto.verify(timestamp, nonce, echostr, sig);
            WeComCrypto.DecryptResult r = crypto.decrypt(echostr);
            if (expected != null && !expected.isBlank() && !expected.equals(r.receivedCorpId())) {
                throw new BizException(401, "corpid mismatch");
            }
            callbackLogger.logOk(ctx, System.currentTimeMillis() - t0);
            return ResponseEntity.ok(r.plainXml());
        } catch (BizException e) {
            callbackLogger.logReject(ctx, e.getMessage(), System.currentTimeMillis() - t0);
            throw e;
        }
    }

    @PostMapping(
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE},
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> receive(@RequestParam("msg_signature") String sig,
                                          @RequestParam String timestamp,
                                          @RequestParam String nonce,
                                          @RequestBody String body) {
        long t0 = System.currentTimeMillis();
        TenantContext.set(TenantContext.DEFAULT_TENANT_ID);
        String expected = props.getWecom().getCorpId();
        Context ctx = Context.ofReceive(TenantContext.DEFAULT_TENANT_ID, null, "default", expected);
        try {
            String ack = doReceiveDefault(ctx, sig, timestamp, nonce, body, t0);
            return ResponseEntity.ok(ack);
        } catch (BizException e) {
            callbackLogger.logReject(ctx, e.getMessage(), System.currentTimeMillis() - t0);
            throw e;
        }
    }

    // ---- 共用实现 ----

    private String doVerify(TenantWecomAppResolver.Resolved r, String sig, String timestamp, String nonce, String echostr) {
        crypto.verify(r.token(), timestamp, nonce, echostr, sig);
        WeComCrypto.DecryptResult dec = crypto.decrypt(r.aesKey(), echostr);
        if (!r.app().getCorpId().equals(dec.receivedCorpId())) {
            throw new BizException(401, "corpid mismatch");
        }
        return dec.plainXml();
    }

    private String doReceive(Context ctx, TenantWecomAppResolver.Resolved r,
                             String sig, String timestamp, String nonce, String body, long t0) {
        String encrypted = parser.parseEncrypt(body);
        if (encrypted == null) throw new BizException(400, "invalid payload");

        crypto.verify(r.token(), timestamp, nonce, encrypted, sig);
        WeComCrypto.DecryptResult dec = crypto.decrypt(r.aesKey(), encrypted);
        if (!r.app().getCorpId().equals(dec.receivedCorpId())) {
            throw new BizException(401, "corpid mismatch");
        }
        return handleDecrypted(ctx, r.tenantId(), r.app().getId(), r.app().getCsAgentId(),
                dec, encrypted, sig, timestamp, nonce, t0);
    }

    private String doReceiveDefault(Context ctx, String sig, String timestamp, String nonce, String body, long t0) {
        String encrypted = parser.parseEncrypt(body);
        if (encrypted == null) throw new BizException(400, "invalid payload");

        crypto.verify(timestamp, nonce, encrypted, sig);
        WeComCrypto.DecryptResult dec = crypto.decrypt(encrypted);
        String expected = props.getWecom().getCorpId();
        if (expected != null && !expected.isBlank() && !expected.equals(dec.receivedCorpId())) {
            throw new BizException(401, "corpid mismatch");
        }
        // 默认租户兼容路径：无 wecomApp 实体，wecomAppId=null，csAgentId=null（走租户默认流程）。
        return handleDecrypted(ctx, TenantContext.DEFAULT_TENANT_ID, null, null,
                dec, encrypted, sig, timestamp, nonce, t0);
    }

    /**
     * P005 F002：落库同时保留密文（encrypted_payload / msg_signature / timestamp / nonce）。
     * 当前走"简化方案"——只在验签+解密都成功后一次性写入；失败分支不入库。
     */
    private String handleDecrypted(Context baseCtx, Long tenantId, Long wecomAppId, Long csAgentId,
                                   WeComCrypto.DecryptResult r, String encryptedPayload,
                                   String sig, String timestamp, String nonce, long t0) {
        WeComMessageParser.Parsed p = parser.parse(r.plainXml());
        Context ctx = baseCtx.withMessage(
                p.getMsgType(), p.getMsgId(), p.getChatId(), p.getFromUsername(),
                p.getContent() == null ? null : p.getContent().length());

        if (p.getMsgId() == null) {
            callbackLogger.logIgnore(ctx, "missing msgid");
            return "success";
        }
        if (!idem.firstSeen(p.getMsgId())) {
            callbackLogger.logIgnore(ctx, "dedup hit");
            return "success";
        }

        try {
            if (!msgRepo.existsByTenantIdAndMsgId(tenantId, p.getMsgId())) {
                WeComMessage m = new WeComMessage();
                m.setTenantId(tenantId);
                m.setMsgId(p.getMsgId());
                m.setGroupId(p.getChatId());
                m.setFromUserid(p.getFromUsername());
                m.setMsgType(p.getMsgType());
                m.setContent(p.getContent());
                m.setMentionedList(p.getMentionedList());
                m.setRaw(r.plainXml());
                m.setEncryptedPayload(encryptedPayload);
                m.setMsgSignature(sig);
                m.setTimestampStr(timestamp);
                m.setNonce(nonce);
                m.setWecomAppId(wecomAppId);
                m.setVerifyStatus(WeComMessage.VERIFY_VERIFIED);
                msgRepo.save(m);
            }
        } catch (Exception e) {
            log.warn("fast-persist wecom_message failed (will continue to Kafka): {}", e.getMessage());
        }

        // 直聊（1:1）消息仅做审计留痕，不走群聊 Kafka 流水线。
        if (p.getChatId() == null) {
            callbackLogger.logIgnore(ctx, "direct-chat persisted (no kafka dispatch)");
            return "success";
        }

        InboundEnvelope env = new InboundEnvelope();
        env.setTenantId(tenantId);
        env.setMsgId(p.getMsgId());
        env.setGroupId(p.getChatId());
        env.setFromUserid(p.getFromUsername());
        env.setMsgType(p.getMsgType());
        env.setContent(p.getContent());
        env.setMentionedList(p.getMentionedList());
        env.setCreateTime(p.getCreateTime());
        env.setConversationId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        env.setCsAgentId(csAgentId);

        kafka.send(props.getKafkaTopics().getInbound(), p.getChatId(), env);
        callbackLogger.logOk(ctx, System.currentTimeMillis() - t0);
        return "success";
    }
}
