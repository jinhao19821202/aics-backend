package com.aics.m001_message.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface WeComMessageRepository extends JpaRepository<WeComMessage, Long> {

    boolean existsByTenantIdAndMsgId(Long tenantId, String msgId);

    /**
     * P005 F003：管理后台消息审计列表查询。
     * 所有 nullable 过滤参数显式 CAST，避免 PG 类型推断把 NULL 识别成 bytea。
     * mentionBotOnly 由 service 预解析 bot_userid 列表后以逗号分隔字符串传入；
     * 用 string_to_array + jsonb_array_elements_text EXISTS，避开 JDBC ? 参数冲突问题。
     */
    @Query(value = """
            SELECT * FROM wecom_message m
            WHERE m.tenant_id = :tenantId
              AND (CAST(:wecomAppId AS bigint) IS NULL OR m.wecom_app_id = :wecomAppId)
              AND (CAST(:chatId AS text) IS NULL OR m.group_id = :chatId)
              AND (CAST(:fromUserid AS text) IS NULL OR m.from_userid = :fromUserid)
              AND (CAST(:msgType AS text) IS NULL OR m.msg_type = :msgType)
              AND (CAST(:fromTime AS timestamptz) IS NULL OR m.created_at >= CAST(:fromTime AS timestamptz))
              AND (CAST(:toTime AS timestamptz) IS NULL OR m.created_at < CAST(:toTime AS timestamptz))
              AND (
                :mentionBotOnly = false
                OR (
                  m.mentioned_list IS NOT NULL
                  AND :botUseridsCsv <> ''
                  AND EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(m.mentioned_list) e
                    WHERE e = ANY(string_to_array(:botUseridsCsv, ','))
                  )
                )
              )
            ORDER BY m.created_at DESC
            """,
            countQuery = """
                    SELECT count(*) FROM wecom_message m
                    WHERE m.tenant_id = :tenantId
                      AND (CAST(:wecomAppId AS bigint) IS NULL OR m.wecom_app_id = :wecomAppId)
                      AND (CAST(:chatId AS text) IS NULL OR m.group_id = :chatId)
                      AND (CAST(:fromUserid AS text) IS NULL OR m.from_userid = :fromUserid)
                      AND (CAST(:msgType AS text) IS NULL OR m.msg_type = :msgType)
                      AND (CAST(:fromTime AS timestamptz) IS NULL OR m.created_at >= CAST(:fromTime AS timestamptz))
                      AND (CAST(:toTime AS timestamptz) IS NULL OR m.created_at < CAST(:toTime AS timestamptz))
                      AND (
                        :mentionBotOnly = false
                        OR (
                          m.mentioned_list IS NOT NULL
                          AND :botUseridsCsv <> ''
                          AND EXISTS (
                            SELECT 1 FROM jsonb_array_elements_text(m.mentioned_list) e
                            WHERE e = ANY(string_to_array(:botUseridsCsv, ','))
                          )
                        )
                      )
                    """,
            nativeQuery = true)
    Page<WeComMessage> search(@Param("tenantId") Long tenantId,
                              @Param("wecomAppId") Long wecomAppId,
                              @Param("chatId") String chatId,
                              @Param("fromUserid") String fromUserid,
                              @Param("msgType") String msgType,
                              @Param("fromTime") OffsetDateTime fromTime,
                              @Param("toTime") OffsetDateTime toTime,
                              @Param("mentionBotOnly") boolean mentionBotOnly,
                              @Param("botUseridsCsv") String botUseridsCsv,
                              Pageable pageable);
}
