package com.aics.m005_admin.csagent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CsAgentKbDocumentMappingRepository extends JpaRepository<CsAgentKbDocumentMapping, Long> {

    List<CsAgentKbDocumentMapping> findByCsAgentId(Long csAgentId);

    long countByCsAgentId(Long csAgentId);

    @Modifying
    @Query("DELETE FROM CsAgentKbDocumentMapping m WHERE m.csAgentId = :agentId AND m.kbDocumentId IN :docIds")
    int deleteByCsAgentIdAndKbDocumentIdIn(@Param("agentId") Long agentId,
                                           @Param("docIds") Collection<Long> docIds);

    @Modifying
    @Query("DELETE FROM CsAgentKbDocumentMapping m WHERE m.csAgentId = :agentId")
    int deleteByCsAgentId(@Param("agentId") Long agentId);
}
