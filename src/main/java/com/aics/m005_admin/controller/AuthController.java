package com.aics.m005_admin.controller;

import com.aics.common.ApiResponse;
import com.aics.common.BizException;
import com.aics.m005_admin.auth.AuthService;
import com.aics.m005_admin.tenant.Tenant;
import com.aics.m005_admin.tenant.TenantRepository;
import com.aics.m005_admin.user.AdminPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final int BRIEF_RL_PER_MIN = 10;

    private final AuthService auth;
    private final TenantRepository tenantRepo;
    private final StringRedisTemplate redis;

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginReq req) {
        return ApiResponse.ok(auth.login(req.getTenantCode(), req.getUsername(), req.getPassword()));
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestBody RefreshReq req) {
        return ApiResponse.ok(auth.refresh(req.getRefreshToken()));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal AdminPrincipal me) {
        if (me == null) return ApiResponse.error(401, "未登录");
        return ApiResponse.ok(Map.of(
                "id", me.id(),
                "tenantId", me.tenantId(),
                "username", me.username(),
                "displayName", me.displayName() == null ? "" : me.displayName(),
                "roles", me.roles(),
                "authorities", me.authorities(),
                "groupScope", me.groupScope()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // JWT 无状态：前端清 token 即可。此处保留端点以便审计/黑名单化扩展。
        return ApiResponse.ok();
    }

    /**
     * 登录页的租户预解析接口：匿名可调用但按来源 IP 限流 10/min，避免被用于枚举租户编码。
     * 命中返回 code/name/plan/status；未命中统一 404（不区分"不存在"与"不可见"）。
     */
    @GetMapping("/tenant-brief")
    public ApiResponse<Map<String, Object>> tenantBrief(@RequestParam("code") String code, HttpServletRequest req) {
        if (code == null || code.isBlank()) {
            throw BizException.of("code required");
        }
        enforceBriefRateLimit(clientIp(req));
        Tenant t = tenantRepo.findByCode(code.trim())
                .orElseThrow(() -> BizException.notFound("租户不存在"));
        String status = t.getStatus();
        if (!"active".equals(status) && !"trial".equals(status)) {
            throw BizException.notFound("租户不存在");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", t.getCode());
        m.put("name", t.getName());
        m.put("plan", t.getPlan());
        m.put("status", status);
        return ApiResponse.ok(m);
    }

    /** 登录态用户改自己的密码；首登强制改密与日常改密共用此端点。 */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@RequestBody ChangePasswordReq req,
                                            @AuthenticationPrincipal AdminPrincipal me) {
        if (me == null) return ApiResponse.error(401, "未登录");
        auth.changePassword(me.id(), req.getOldPassword(), req.getNewPassword());
        return ApiResponse.ok();
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return req.getRemoteAddr();
    }

    private void enforceBriefRateLimit(String ip) {
        if (ip == null || ip.isBlank()) ip = "unknown";
        long minute = Instant.now().getEpochSecond() / 60;
        String key = "auth:brief:rl:" + ip + ":m" + minute;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(70));
        }
        if (count != null && count > BRIEF_RL_PER_MIN) {
            throw new BizException(429, "请求过于频繁，请稍后再试");
        }
    }

    @Data
    public static class LoginReq {
        /** 租户 code；省略时服务端会尝试按 username 做无歧义回退。*/
        private String tenantCode;
        @NotBlank private String username;
        @NotBlank private String password;
    }

    @Data
    public static class RefreshReq {
        @NotBlank private String refreshToken;
    }

    @Data
    public static class ChangePasswordReq {
        @NotBlank private String oldPassword;
        @NotBlank private String newPassword;
    }
}
