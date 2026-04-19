package com.aics.m005_admin.security;

import com.aics.common.tenant.TenantContext;
import com.aics.m005_admin.auth.AuthService;
import com.aics.m005_admin.auth.JwtService;
import com.aics.m005_admin.user.AdminPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 从 Authorization: Bearer ... 解析 access token，加载 AdminPrincipal 放入 SecurityContext；
 * 同时把 tenantId 注入 TenantContext（ThreadLocal）。
 *
 * 仅接受 iss=aics-admin 的 token；其他 issuer（如 aics-ops）不在此 filter 生效，避免跨用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final AuthService auth;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(7);
        try {
            Map<String, Object> c = jwt.decode(token);
            if (!JwtService.ISSUER_ADMIN.equals(c.get("iss"))) {
                chain.doFilter(request, response);
                return;
            }
            if (!"access".equals(c.get("type"))) {
                chain.doFilter(request, response);
                return;
            }
            Long userId = (Long) c.get("userId");
            Long tenantId = (Long) c.get("tenantId");
            AdminPrincipal p = auth.loadPrincipal(userId);
            if (p == null) {
                chain.doFilter(request, response);
                return;
            }
            // 额外校验：token 中的 tenantId 必须与数据库中该用户的 tenantId 一致
            if (tenantId != null && !tenantId.equals(p.tenantId())) {
                log.warn("tenantId mismatch for user {}: token={}, db={}", userId, tenantId, p.tenantId());
                chain.doFilter(request, response);
                return;
            }

            TenantContext.set(p.tenantId());

            List<SimpleGrantedAuthority> auths = Stream.concat(
                    p.authorities() == null ? Stream.<String>empty() : p.authorities().stream(),
                    p.roles() == null ? Stream.<String>empty() : p.roles().stream().map(r -> "ROLE_" + r))
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(p, null, auths);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            log.debug("jwt parse failed: {}", e.getMessage());
        }
        chain.doFilter(request, response);
    }
}
