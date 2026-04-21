package com.aics.m001_message.audit;

import com.aics.common.BizException;
import com.aics.m001_message.domain.SessionMessage;
import com.aics.m001_message.domain.SessionMessageRepository;
import com.aics.m001_message.domain.WeComMessage;
import com.aics.m001_message.domain.WeComMessageRepository;
import com.aics.m005_admin.tenant.TenantCsAgent;
import com.aics.m005_admin.tenant.TenantCsAgentRepository;
import com.aics.m005_admin.tenant.TenantWecomApp;
import com.aics.m005_admin.tenant.TenantWecomAppRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * P005 F003：企微消息审计查询服务。
 * 过滤条件全部以当前租户为守护；跨租户调用直接 404。
 * mentionBotOnly 时预取租户下 enabled 应用的 bot_userid 列表，以 CSV 形式下推给 Repository 原生查询。
 */
@Service
@RequiredArgsConstructor
public class WeComMessageAuditService {

    private final WeComMessageRepository msgRepo;
    private final TenantWecomAppRepository wecomRepo;
    private final TenantCsAgentRepository csAgentRepo;
    private final SessionMessageRepository sessionRepo;

    private static final int PREVIEW_LEN = 30;

    public ListResult list(Long tenantId, Query q) {
        String botUseridsCsv = "";
        if (q.mentionBotOnly()) {
            List<TenantWecomApp> apps = q.wecomAppId() == null
                    ? wecomRepo.findByTenantIdAndEnabledTrue(tenantId)
                    : wecomRepo.findById(q.wecomAppId())
                        .filter(a -> tenantId.equals(a.getTenantId()))
                        .map(List::of)
                        .orElseGet(List::of);
            botUseridsCsv = apps.stream()
                    .map(TenantWecomApp::getBotUserid)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.joining(","));
        }
        PageRequest pr = PageRequest.of(Math.max(q.page() - 1, 0), q.size());
        Page<WeComMessage> pg = msgRepo.search(
                tenantId,
                q.wecomAppId(),
                blankToNull(q.chatId()),
                blankToNull(q.fromUserid()),
                blankToNull(q.msgType()),
                q.from(),
                q.to(),
                q.mentionBotOnly(),
                botUseridsCsv,
                pr
        );

        Map<Long, TenantWecomApp> appById = loadAppsForTenant(tenantId);
        Map<Long, String> appBotUseridById = appById.values().stream()
                .filter(a -> a.getBotUserid() != null && !a.getBotUserid().isBlank())
                .collect(Collectors.toMap(TenantWecomApp::getId, TenantWecomApp::getBotUserid));

        List<WeComMessageAuditDto.ListItem> items = pg.getContent().stream()
                .map(m -> toListItem(m, appById, appBotUseridById))
                .toList();
        return new ListResult(pg.getTotalElements(), items);
    }

    public WeComMessageAuditDto.Detail detail(Long tenantId, Long id) {
        WeComMessage m = msgRepo.findById(id)
                .filter(x -> tenantId.equals(x.getTenantId()))
                .orElseThrow(() -> new BizException(404, "message not found"));

        WeComMessageAuditDto.Detail d = new WeComMessageAuditDto.Detail();
        d.setId(m.getId());
        d.setTenantId(m.getTenantId());
        d.setWecomAppId(m.getWecomAppId());
        d.setMsgId(m.getMsgId());
        d.setChatId(m.getGroupId());
        d.setFromUserid(m.getFromUserid());
        d.setFromName(m.getFromName());
        d.setMsgType(m.getMsgType());
        d.setContent(m.getContent());
        d.setMentionedList(m.getMentionedList());
        d.setVerifyStatus(m.getVerifyStatus());
        d.setRaw(m.getRaw());
        d.setEncryptedPayload(m.getEncryptedPayload());
        d.setMsgSignature(m.getMsgSignature());
        d.setTimestamp(m.getTimestampStr());
        d.setNonce(m.getNonce());
        d.setCreatedAt(m.getCreatedAt());

        if (m.getWecomAppId() != null) {
            wecomRepo.findById(m.getWecomAppId())
                    .filter(a -> tenantId.equals(a.getTenantId()))
                    .ifPresent(app -> {
                        WeComMessageAuditDto.WeComAppBrief b = new WeComMessageAuditDto.WeComAppBrief();
                        b.setId(app.getId());
                        b.setName(app.getName());
                        b.setCorpId(app.getCorpId());
                        b.setAgentId(app.getAgentId());
                        d.setWecomApp(b);
                        if (app.getCsAgentId() != null) {
                            csAgentRepo.findById(app.getCsAgentId())
                                    .filter(a -> tenantId.equals(a.getTenantId()))
                                    .ifPresent(agent -> {
                                        WeComMessageAuditDto.CsAgentBrief cb = new WeComMessageAuditDto.CsAgentBrief();
                                        cb.setId(agent.getId());
                                        cb.setName(agent.getName());
                                        cb.setCode(agent.getCode());
                                        d.setCsAgent(cb);
                                    });
                        }
                    });
        }

        if (m.getMsgId() != null) {
            Optional<SessionMessage> linked = sessionRepo.findFirstByTenantIdAndMsgIdAndRole(
                    tenantId, m.getMsgId(), "user");
            linked.ifPresent(sm -> d.setLinkedSessionMsgId(sm.getId()));
        }
        return d;
    }

    private WeComMessageAuditDto.ListItem toListItem(WeComMessage m,
                                                     Map<Long, TenantWecomApp> appById,
                                                     Map<Long, String> appBotUseridById) {
        WeComMessageAuditDto.ListItem li = new WeComMessageAuditDto.ListItem();
        li.setId(m.getId());
        li.setCreatedAt(m.getCreatedAt());
        li.setWecomAppId(m.getWecomAppId());
        if (m.getWecomAppId() != null) {
            TenantWecomApp app = appById.get(m.getWecomAppId());
            if (app != null) li.setWecomAppName(app.getName());
        }
        li.setChatId(m.getGroupId());
        li.setFromUserid(m.getFromUserid());
        li.setMsgType(m.getMsgType());
        li.setContentPreview(preview(m.getContent()));
        li.setVerifyStatus(m.getVerifyStatus());
        li.setMentionedBot(calcMentionedBot(m, appBotUseridById));
        return li;
    }

    private static Boolean calcMentionedBot(WeComMessage m, Map<Long, String> appBotUseridById) {
        if (m.getMentionedList() == null || m.getMentionedList().isEmpty()) return false;
        if (m.getWecomAppId() == null) {
            return appBotUseridById.values().stream().anyMatch(b -> m.getMentionedList().contains(b));
        }
        String bot = appBotUseridById.get(m.getWecomAppId());
        return bot != null && m.getMentionedList().contains(bot);
    }

    private Map<Long, TenantWecomApp> loadAppsForTenant(Long tenantId) {
        Map<Long, TenantWecomApp> out = new HashMap<>();
        for (TenantWecomApp a : wecomRepo.findByTenantId(tenantId)) {
            out.put(a.getId(), a);
        }
        return out;
    }

    static String preview(String s) {
        if (s == null) return null;
        // 折叠换行 / 回车 / 制表符为单个空格，避免列表列被渲染得支离破碎
        String trimmed = s.replaceAll("\\s+", " ").trim();
        return trimmed.length() <= PREVIEW_LEN ? trimmed : trimmed.substring(0, PREVIEW_LEN) + "…";
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    public record Query(
            Long wecomAppId,
            String chatId,
            String fromUserid,
            String msgType,
            OffsetDateTime from,
            OffsetDateTime to,
            boolean mentionBotOnly,
            int page,
            int size
    ) {}

    public record ListResult(long total, List<WeComMessageAuditDto.ListItem> items) {}
}
