package com.aics.m001_message.audit;

import com.aics.common.ApiResponse;
import com.aics.common.PageResult;
import com.aics.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/**
 * P005 F003：企微消息审计 API。
 * GET  /api/admin/wecom-messages         列表（过滤 + 分页）
 * GET  /api/admin/wecom-messages/{id}    详情（明文 XML / 原始密文 / 路由上下文 / 关联会话 msg）
 *
 * 权限：msg:wecom:read（默认挂到 TENANT_ADMIN、OPS_VIEWER；一线客服不给）。
 * 租户守护：TenantContext.require() 强制租户过滤，跨租户直接 404。
 */
@RestController
@RequestMapping("/api/admin/wecom-messages")
@RequiredArgsConstructor
public class WeComMessageAuditController {

    private final WeComMessageAuditService service;

    @GetMapping
    @PreAuthorize("hasAuthority('msg:wecom:read')")
    public ApiResponse<PageResult<WeComMessageAuditDto.ListItem>> list(
            @RequestParam(required = false) Long wecomAppId,
            @RequestParam(required = false) String chatId,
            @RequestParam(required = false) String fromUserid,
            @RequestParam(required = false) String msgType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "false") boolean mentionBotOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long tenantId = TenantContext.require();
        WeComMessageAuditService.Query q = new WeComMessageAuditService.Query(
                wecomAppId, chatId, fromUserid, msgType, from, to, mentionBotOnly, page, size);
        WeComMessageAuditService.ListResult r = service.list(tenantId, q);
        return ApiResponse.ok(PageResult.of(r.total(), r.items()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('msg:wecom:read')")
    public ApiResponse<WeComMessageAuditDto.Detail> detail(@PathVariable Long id) {
        Long tenantId = TenantContext.require();
        return ApiResponse.ok(service.detail(tenantId, id));
    }
}
