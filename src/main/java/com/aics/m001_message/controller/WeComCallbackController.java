package com.aics.m001_message.controller;

import com.aics.common.BizException;
import com.aics.common.tenant.TenantContext;
import com.aics.config.AppProperties;
import com.aics.infra.wecom.WeComCrypto;
import com.aics.infra.wecom.WeComMessageParser;
import com.aics.m001_message.domain.WeComMessage;
import com.aics.m001_message.domain.WeComMessageRepository;
import com.aics.m001_message.dto.InboundEnvelope;
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
    /** P004-A F001：用 ObjectProvider 避免与 TenantWecomAppService 之间的循环依赖初始化问题。 */
    private final ObjectProvider<TenantWecomAppService> wecomAppServiceProvider;

    // ---- tenantCode 多租户入口 ----

    @GetMapping(value = "/{tenantCode}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verifyTenant(@PathVariable String tenantCode,
                                               @RequestParam("msg_signature") String sig,
                                               @RequestParam String timestamp,
                                               @RequestParam String nonce,
                                               @RequestParam String echostr) {
        TenantWecomAppResolver.Resolved r = tenantResolver.resolveByTenantCode(tenantCode);
        TenantContext.set(r.tenantId());
        String echo = doVerify(r, sig, timestamp, nonce, echostr);
        // P004-A F001：首次验签通过 → 写 status=VERIFIED
        TenantWecomAppService svc = wecomAppServiceProvider.getIfAvailable();
        if (svc != null) {
            try {
                svc.markVerified(r.app().getId());
            } catch (Exception e) {
                log.warn("markVerified failed for wecomAppId={}: {}", r.app().getId(), e.getMessage());
            }
        }
        return ResponseEntity.ok(echo);
    }

    @PostMapping(value = "/{tenantCode}", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> receiveTenant(@PathVariable String tenantCode,
                                                @RequestParam("msg_signature") String sig,
                                                @RequestParam String timestamp,
                                                @RequestParam String nonce,
                                                @RequestBody String body) {
        TenantWecomAppResolver.Resolved r = tenantResolver.resolveByTenantCode(tenantCode);
        TenantContext.set(r.tenantId());
        return ResponseEntity.ok(doReceive(r, sig, timestamp, nonce, body));
    }

    // ---- 默认租户兼容入口（M1 过渡）----

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(@RequestParam("msg_signature") String sig,
                                         @RequestParam String timestamp,
                                         @RequestParam String nonce,
                                         @RequestParam String echostr) {
        TenantContext.set(TenantContext.DEFAULT_TENANT_ID);
        crypto.verify(timestamp, nonce, echostr, sig);
        WeComCrypto.DecryptResult r = crypto.decrypt(echostr);
        String expected = props.getWecom().getCorpId();
        if (expected != null && !expected.isBlank() && !expected.equals(r.receivedCorpId())) {
            throw new BizException(401, "corpid mismatch");
        }
        return ResponseEntity.ok(r.plainXml());
    }

    @PostMapping(consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> receive(@RequestParam("msg_signature") String sig,
                                          @RequestParam String timestamp,
                                          @RequestParam String nonce,
                                          @RequestBody String body) {
        TenantContext.set(TenantContext.DEFAULT_TENANT_ID);
        return ResponseEntity.ok(doReceiveDefault(sig, timestamp, nonce, body));
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

    private String doReceive(TenantWecomAppResolver.Resolved r, String sig, String timestamp, String nonce, String body) {
        String encrypted = parser.parseEncrypt(body);
        if (encrypted == null) throw new BizException(400, "invalid payload");

        crypto.verify(r.token(), timestamp, nonce, encrypted, sig);
        WeComCrypto.DecryptResult dec = crypto.decrypt(r.aesKey(), encrypted);
        if (!r.app().getCorpId().equals(dec.receivedCorpId())) {
            throw new BizException(401, "corpid mismatch");
        }
        return handleDecrypted(r.tenantId(), r.app().getCsAgentId(), dec);
    }

    private String doReceiveDefault(String sig, String timestamp, String nonce, String body) {
        String encrypted = parser.parseEncrypt(body);
        if (encrypted == null) throw new BizException(400, "invalid payload");

        crypto.verify(timestamp, nonce, encrypted, sig);
        WeComCrypto.DecryptResult dec = crypto.decrypt(encrypted);
        String expected = props.getWecom().getCorpId();
        if (expected != null && !expected.isBlank() && !expected.equals(dec.receivedCorpId())) {
            throw new BizException(401, "corpid mismatch");
        }
        // 默认租户兼容路径：无 wecomApp 实体，csAgentId=null（走租户默认流程）。
        return handleDecrypted(TenantContext.DEFAULT_TENANT_ID, null, dec);
    }

    private String handleDecrypted(Long tenantId, Long csAgentId, WeComCrypto.DecryptResult r) {
        WeComMessageParser.Parsed p = parser.parse(r.plainXml());
        if (p.getMsgId() == null || p.getChatId() == null) {
            log.info("ignore non-group or missing msgid: type={}", p.getMsgType());
            return "success";
        }
        if (!idem.firstSeen(p.getMsgId())) {
            log.debug("dedup hit: {}", p.getMsgId());
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
                msgRepo.save(m);
            }
        } catch (Exception e) {
            log.warn("fast-persist wecom_message failed (will continue to Kafka): {}", e.getMessage());
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
        return "success";
    }
}
