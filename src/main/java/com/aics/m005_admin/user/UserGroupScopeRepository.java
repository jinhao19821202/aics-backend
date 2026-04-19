package com.aics.m005_admin.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserGroupScopeRepository extends JpaRepository<UserGroupScope, UserGroupScope.PK> {

    List<UserGroupScope> findByTenantIdAndUserId(Long tenantId, Long userId);

    @Modifying
    @Query("delete from UserGroupScope s where s.tenantId = :tenantId and s.userId = :userId")
    void deleteByTenantIdAndUserId(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
