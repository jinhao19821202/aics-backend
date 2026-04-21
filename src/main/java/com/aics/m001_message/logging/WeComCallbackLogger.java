package com.aics.m001_message.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * P005 F001：企微回调统一结构化日志。
 *
 * 格式：
 *   wecom-callback: action={VERIFY|RECEIVE} result={OK|REJECT} tenantId=.. wecomAppId=..
 *     tenantCode=.. corpId=.. msgType=.. msgId=.. chatId=.. fromUserid=.. contentLen=.. costMs=..
 *
 * 敏感字段绝不进日志（token / aes_key / secret / signature / content 原文 / encrypted_payload）。
 * content 只记 length，原文需管理后台按 msgId 查询。
 */
@Slf4j
@Component
public class WeComCallbackLogger {

    public enum Action { VERIFY, RECEIVE }

    public void logOk(Context ctx, long costMs) {
        log.info(
                "wecom-callback: action={} result=OK tenantId={} wecomAppId={} tenantCode={} corpId={} " +
                        "msgType={} msgId={} chatId={} fromUserid={} contentLen={} costMs={}",
                ctx.action(), dash(ctx.tenantId()), dash(ctx.wecomAppId()), dash(ctx.tenantCode()),
                dash(ctx.corpId()), dash(ctx.msgType()), dash(ctx.msgId()), dash(ctx.chatId()),
                dash(ctx.fromUserid()), dash(ctx.contentLen()), costMs
        );
    }

    public void logReject(Context ctx, String reason, long costMs) {
        log.warn(
                "wecom-callback: action={} result=REJECT reason=\"{}\" tenantId={} wecomAppId={} tenantCode={} " +
                        "corpId={} msgType={} msgId={} chatId={} fromUserid={} costMs={}",
                ctx.action(), reason, dash(ctx.tenantId()), dash(ctx.wecomAppId()), dash(ctx.tenantCode()),
                dash(ctx.corpId()), dash(ctx.msgType()), dash(ctx.msgId()), dash(ctx.chatId()),
                dash(ctx.fromUserid()), costMs
        );
    }

    public void logIgnore(Context ctx, String reason) {
        log.info(
                "wecom-callback: action={} result=IGNORE reason=\"{}\" tenantId={} wecomAppId={} tenantCode={} " +
                        "msgType={} msgId={} chatId={}",
                ctx.action(), reason, dash(ctx.tenantId()), dash(ctx.wecomAppId()), dash(ctx.tenantCode()),
                dash(ctx.msgType()), dash(ctx.msgId()), dash(ctx.chatId())
        );
    }

    private static String dash(Object v) {
        if (v == null) return "-";
        String s = v.toString();
        return s.isEmpty() ? "-" : s;
    }

    /** 链路上下文；随着处理推进（验签 → 解密 → 解析）逐步 withXxx 补齐。 */
    public record Context(
            Action action,
            Long tenantId,
            Long wecomAppId,
            String tenantCode,
            String corpId,
            String msgType,
            String msgId,
            String chatId,
            String fromUserid,
            Integer contentLen
    ) {
        public static Context ofVerify(Long tenantId, Long wecomAppId, String tenantCode, String corpId) {
            return new Context(Action.VERIFY, tenantId, wecomAppId, tenantCode, corpId, null, null, null, null, null);
        }
        public static Context ofReceive(Long tenantId, Long wecomAppId, String tenantCode, String corpId) {
            return new Context(Action.RECEIVE, tenantId, wecomAppId, tenantCode, corpId, null, null, null, null, null);
        }
        public Context withMessage(String msgType, String msgId, String chatId, String fromUserid, Integer contentLen) {
            return new Context(action, tenantId, wecomAppId, tenantCode, corpId, msgType, msgId, chatId, fromUserid, contentLen);
        }
    }
}
