package com.aics.m005_admin.csagent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CsAgentFaqGroupMappingRepository extends JpaRepository<CsAgentFaqGroupMapping, Long> {

    List<CsAgentFaqGroupMapping> findByCsAgentId(Long csAgentId);

    long countByCsAgentId(Long csAgentId);

    @Modifying
    @Query("DELETE FROM CsAgentFaqGroupMapping m WHERE m.csAgentId = :agentId AND m.faqGroupId IN :groupIds")
    int deleteByCsAgentIdAndFaqGroupIdIn(@Param("agentId") Long agentId,
                                         @Param("groupIds") Collection<Long> groupIds);

    @Modifying
    @Query("DELETE FROM CsAgentFaqGroupMapping m WHERE m.csAgentId = :agentId")
    int deleteByCsAgentId(@Param("agentId") Long agentId);
}
