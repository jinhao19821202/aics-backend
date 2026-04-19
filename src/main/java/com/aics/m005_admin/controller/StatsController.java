package com.aics.m005_admin.controller;

import com.aics.common.ApiResponse;
import com.aics.m005_admin.stats.StatsService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService stats;
    private final CircuitBreakerRegistry cbRegistry;

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('stats:read')")
    public ApiResponse<Map<String, Object>> summary(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(stats.summary(days));
    }

    @GetMapping("/trend")
    @PreAuthorize("hasAuthority('stats:read')")
    public ApiResponse<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(stats.timeSeries(days));
    }

    @GetMapping("/circuit")
    @PreAuthorize("hasAuthority('stats:read')")
    public ApiResponse<Map<String, Object>> circuit() {
        var cb = cbRegistry.circuitBreaker("llmQwen");
        var m = cb.getMetrics();
        return ApiResponse.ok(Map.of(
                "state", cb.getState().name(),
                "failureRate", m.getFailureRate(),
                "slowCallRate", m.getSlowCallRate(),
                "buffered", m.getNumberOfBufferedCalls(),
                "failed", m.getNumberOfFailedCalls(),
                "successful", m.getNumberOfSuccessfulCalls(),
                "slow", m.getNumberOfSlowCalls()));
    }
}
