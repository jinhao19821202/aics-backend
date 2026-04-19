package com.aics.m005_admin.auth;

import com.aics.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 轻量 JWT 工具：HS256，access 8h，refresh 7d。租户管理后台签发，iss=aics-admin。 */
@Component
@RequiredArgsConstructor
public class JwtService {

    public static final String ISSUER_ADMIN = "aics-admin";

    private final AppProperties props;

    public String issueAccess(Long userId, Long tenantId, String tenantCode, String username, List<String> roles) {
        return build(userId, tenantId, tenantCode, username, roles, "access",
                props.getJwt().getExpireHours(), ChronoUnit.HOURS);
    }

    public String issueRefresh(Long userId, Long tenantId, String tenantCode, String username) {
        return build(userId, tenantId, tenantCode, username, List.of(), "refresh",
                props.getJwt().getRefreshDays(), ChronoUnit.DAYS);
    }

    private String build(Long userId, Long tenantId, String tenantCode, String username, List<String> roles, String type,
                         long amount, ChronoUnit unit) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER_ADMIN)
                .subject(String.valueOf(userId))
                .claim("tenantId", tenantId)
                .claim("tenantCode", tenantCode)
                .claim("username", username)
                .claim("roles", roles)
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(amount, unit)))
                .signWith(key())
                .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
        } catch (JwtException e) {
            throw new IllegalArgumentException("invalid token: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> decode(String token) {
        Claims c = parse(token);
        Map<String, Object> m = new HashMap<>();
        m.put("iss", c.getIssuer());
        m.put("userId", Long.parseLong(c.getSubject()));
        Object tid = c.get("tenantId");
        m.put("tenantId", tid == null ? null : ((Number) tid).longValue());
        m.put("tenantCode", c.get("tenantCode", String.class));
        m.put("username", c.get("username", String.class));
        m.put("roles", c.get("roles", List.class));
        m.put("type", c.get("type", String.class));
        return m;
    }

    private SecretKey key() {
        byte[] bytes = props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(bytes);
    }
}
