package com.aics.m004_handoff.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupAgentMappingRepository extends JpaRepository<GroupAgentMapping, Long> {

    Optional<GroupAgentMapping> findByTenantIdAndGroupId(Long tenantId, String groupId);
}
