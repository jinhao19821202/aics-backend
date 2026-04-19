package com.aics.m005_admin.audit;

import com.aics.common.tenant.TenantContext;
import com.aics.m005_admin.user.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 管理员关键操作审计埋点。调用方写 operator/action，当前 HTTP 请求的 ip/UA 自动收集。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditLogger {

    private final AdminAuditLogRepository repo;

    public void record(AdminPrincipal op, String action, String resourceType, String resourceId,
                       Map<String, Object> before, Map<String, Object> after) {
        try {
            AdminAuditLog l = new AdminAuditLog();
            Long tenantId = op != null ? op.tenantId() : TenantContext.currentOrNull();
            l.setTenantId(tenantId != null ? tenantId : TenantContext.DEFAULT_TENANT_ID);
            if (op != null) {
                l.setAdminId(op.id());
                l.setAdminName(op.username());
            }
            l.setAction(action);
            l.setResourceType(resourceType);
            l.setResourceId(resourceId);
            l.setBeforeVal(before);
            l.setAfterVal(after);
            HttpServletRequest req = currentRequest();
            if (req != null) {
                l.setIp(clientIp(req));
                l.setUserAgent(truncate(req.getHeader("User-Agent"), 255));
            }
            repo.save(l);
        } catch (Exception e) {
            log.warn("admin audit failed: {}", e.getMessage());
        }
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
