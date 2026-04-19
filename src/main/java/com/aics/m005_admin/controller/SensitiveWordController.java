package com.aics.m005_admin.controller;

import com.aics.common.ApiResponse;
import com.aics.common.BizException;
import com.aics.common.PageResult;
import com.aics.common.tenant.TenantContext;
import com.aics.m005_admin.audit.AdminAuditLogger;
import com.aics.m005_admin.sensitive.SensitiveHitLog;
import com.aics.m005_admin.sensitive.SensitiveHitLogRepository;
import com.aics.m005_admin.sensitive.SensitiveWord;
import com.aics.m005_admin.sensitive.SensitiveWordRepository;
import com.aics.m005_admin.sensitive.SensitiveWordService;
import com.aics.m005_admin.user.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/sensitive")
@RequiredArgsConstructor
public class SensitiveWordController {

    private final SensitiveWordRepository repo;
    private final SensitiveHitLogRepository hitRepo;
    private final SensitiveWordService service;
    private final AdminAuditLogger audit;

    @GetMapping("/words")
    @PreAuthorize("hasAuthority('sensitive:read')")
    public ApiResponse<PageResult<SensitiveWord>> list(@RequestParam(required = false) String keyword,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        Long tenantId = TenantContext.require();
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), size);
        Page<SensitiveWord> pg = (keyword == null || keyword.isBlank())
                ? repo.findByTenantIdOrderByIdDesc(tenantId, pr)
                : repo.findByTenantIdAndWordContainingIgnoreCaseOrderByIdDesc(tenantId, keyword, pr);
        return ApiResponse.ok(PageResult.of(pg));
    }

    @PostMapping("/words")
    @PreAuthorize("hasAuthority('sensitive:write')")
    @Transactional
    public ApiResponse<SensitiveWord> create(@RequestBody SensitiveWord req,
                                             @AuthenticationPrincipal AdminPrincipal op) {
        if (req.getWord() == null || req.getWord().isBlank()) throw BizException.of("word 必填");
        if (req.getCategory() == null) req.setCategory("CUSTOM");
        if (req.getLevel() == null) req.setLevel("MEDIUM");
        if (req.getAction() == null) req.setAction("MASK");
        if (req.getEnabled() == null) req.setEnabled(true);
        req.setTenantId(TenantContext.require());
        if (op != null) req.setCreatedBy(op.id());
        SensitiveWord saved = repo.save(req);
        service.publishReload();
        audit.record(op, "sensitive.create", "sensitive_word", String.valueOf(saved.getId()), null,
                Map.of("word", saved.getWord(), "action", saved.getAction()));
        return ApiResponse.ok(saved);
    }

    @PutMapping("/words/{id}")
    @PreAuthorize("hasAuthority('sensitive:write')")
    @Transactional
    public ApiResponse<SensitiveWord> update(@PathVariable Long id, @RequestBody SensitiveWord req,
                                             @AuthenticationPrincipal AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        SensitiveWord w = repo.findByIdAndTenantId(id, tenantId).orElseThrow(() -> BizException.notFound("敏感词不存在"));
        Map<String, Object> before = Map.of("word", w.getWord(), "action", w.getAction(), "enabled", w.getEnabled());
        if (req.getWord() != null) w.setWord(req.getWord());
        if (req.getCategory() != null) w.setCategory(req.getCategory());
        if (req.getLevel() != null) w.setLevel(req.getLevel());
        if (req.getAction() != null) w.setAction(req.getAction());
        if (req.getEnabled() != null) w.setEnabled(req.getEnabled());
        w = repo.save(w);
        service.publishReload();
        audit.record(op, "sensitive.update", "sensitive_word", String.valueOf(id), before,
                Map.of("word", w.getWord(), "action", w.getAction(), "enabled", w.getEnabled()));
        return ApiResponse.ok(w);
    }

    @DeleteMapping("/words/{id}")
    @PreAuthorize("hasAuthority('sensitive:write')")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        repo.findByIdAndTenantId(id, tenantId).ifPresent(w -> {
            repo.delete(w);
            service.publishReload();
            audit.record(op, "sensitive.delete", "sensitive_word", String.valueOf(id),
                    Map.of("word", w.getWord()), null);
        });
        return ApiResponse.ok();
    }

    @PostMapping("/reload")
    @PreAuthorize("hasAuthority('sensitive:write')")
    public ApiResponse<Void> reload(@AuthenticationPrincipal AdminPrincipal op) {
        service.publishReload();
        audit.record(op, "sensitive.reload", "sensitive_word", null, null, null);
        return ApiResponse.ok();
    }

    @PostMapping("/test")
    @PreAuthorize("hasAuthority('sensitive:read')")
    public ApiResponse<Map<String, Object>> test(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        SensitiveWordService.CheckResult in = service.checkInbound(null, text);
        return ApiResponse.ok(Map.of(
                "blocked", in.isBlocked(),
                "processed", in.getProcessedText(),
                "hitWord", in.getHitWord() == null ? "" : in.getHitWord(),
                "level", in.getLevel() == null ? "" : in.getLevel()));
    }

    @GetMapping("/hits")
    @PreAuthorize("hasAuthority('sensitive:read')")
    public ApiResponse<PageResult<SensitiveHitLog>> hits(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int size,
                                                         @RequestParam(required = false, defaultValue = "30") int days) {
        Long tenantId = TenantContext.require();
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), size);
        OffsetDateTime from = OffsetDateTime.now().minusDays(days);
        return ApiResponse.ok(PageResult.of(
                hitRepo.findByTenantIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(tenantId, from, pr)));
    }
}
