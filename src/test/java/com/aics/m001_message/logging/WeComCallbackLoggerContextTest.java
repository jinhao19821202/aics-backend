package com.aics.m001_message.logging;

import com.aics.m001_message.logging.WeComCallbackLogger.Action;
import com.aics.m001_message.logging.WeComCallbackLogger.Context;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * P005 T6：{@link WeComCallbackLogger.Context} 纯数据类的状态机测试。
 * 确保 ofVerify / ofReceive / withMessage 不会互相污染字段，避免调用方错用导致日志串行。
 */
class WeComCallbackLoggerContextTest {

    @Test
    void ofVerify_onlyBasicFields_messageFieldsAreNull() {
        Context c = Context.ofVerify(1L, 4L, "acme", "ww123");
        assertEquals(Action.VERIFY, c.action());
        assertEquals(1L, c.tenantId());
        assertEquals(4L, c.wecomAppId());
        assertEquals("acme", c.tenantCode());
        assertEquals("ww123", c.corpId());
        assertNull(c.msgId());
        assertNull(c.chatId());
        assertNull(c.msgType());
        assertNull(c.fromUserid());
        assertNull(c.contentLen());
    }

    @Test
    void ofReceive_onlyBasicFields_messageFieldsAreNull() {
        Context c = Context.ofReceive(2L, null, "default", "wwX");
        assertEquals(Action.RECEIVE, c.action());
        assertEquals(2L, c.tenantId());
        assertNull(c.wecomAppId());
    }

    @Test
    void withMessage_preservesBaseAndAppendsMessageFields() {
        Context base = Context.ofReceive(3L, 7L, "tn", "wwC");
        Context filled = base.withMessage("text", "MID-1", "CHAT-1", "user-1", 42);
        assertEquals(Action.RECEIVE, filled.action());
        assertEquals(3L, filled.tenantId());
        assertEquals(7L, filled.wecomAppId());
        assertEquals("tn", filled.tenantCode());
        assertEquals("wwC", filled.corpId());
        assertEquals("text", filled.msgType());
        assertEquals("MID-1", filled.msgId());
        assertEquals("CHAT-1", filled.chatId());
        assertEquals("user-1", filled.fromUserid());
        assertEquals(42, filled.contentLen());
        // 原 base 保持不变（record 不可变）
        assertNull(base.msgId());
    }
}
