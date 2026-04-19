package com.aics.common.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP 入口租户上下文清理兜底。
 *
 * 真正的 tenantId 注入发生在 JwtAuthFilter（读 JWT 的 tenantId 声明）或
 * WeComCallbackController（由 URL 路径 tenantCode 查 tenant_wecom_app）。
 *
 * 本 Filter 只负责 finally 清理，确保线程复用时不会串租户。
 */
@Order(2)
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }
}
