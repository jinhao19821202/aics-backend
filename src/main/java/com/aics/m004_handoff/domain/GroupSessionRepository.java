package com.aics.m004_handoff.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupSessionRepository extends JpaRepository<GroupSession, Long> {

    Optional<GroupSession> findByTenantIdAndGroupId(Long tenantId, String groupId);
}
