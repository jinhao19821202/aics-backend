package com.aics.m005_admin.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminPermissionRepository extends JpaRepository<AdminPermission, Long> {
    List<AdminPermission> findByCodeIn(List<String> codes);
}
