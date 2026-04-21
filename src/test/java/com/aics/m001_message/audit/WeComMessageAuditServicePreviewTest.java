package com.aics.m001_message.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * P005 T6：列表页 contentPreview 截断逻辑。
 * 30 字符以内原样返回；超过则追加 "…"；换行折叠为空格；null 返回 null。
 */
class WeComMessageAuditServicePreviewTest {

    @Test
    void nullReturnsNull() {
        assertNull(WeComMessageAuditService.preview(null));
    }

    @Test
    void shortContent_returnedVerbatim() {
        assertEquals("hello", WeComMessageAuditService.preview("hello"));
    }

    @Test
    void newlinesFoldedToSpace() {
        assertEquals("a b c", WeComMessageAuditService.preview("a\nb\r\nc"));
    }

    @Test
    void longContentTruncatedWithEllipsis() {
        String s = "1234567890123456789012345678901234567890"; // 40 chars
        String out = WeComMessageAuditService.preview(s);
        assertEquals("123456789012345678901234567890…", out);
        assertEquals(30 + 1, out.length()); // 30 + 省略号
    }

    @Test
    void boundaryExactly30_noEllipsis() {
        String s = "123456789012345678901234567890"; // exactly 30
        assertEquals(s, WeComMessageAuditService.preview(s));
    }
}
