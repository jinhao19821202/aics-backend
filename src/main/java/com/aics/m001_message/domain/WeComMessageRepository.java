package com.aics.m001_message.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WeComMessageRepository extends JpaRepository<WeComMessage, Long> {

    boolean existsByTenantIdAndMsgId(Long tenantId, String msgId);
}
