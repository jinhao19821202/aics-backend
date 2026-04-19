package com.aics.m005_admin.user;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "admin_role")
public class AdminRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 租户角色 tenant_id = 租户；系统角色（OPS_* 等）tenant_id 为 null。V6 起 (tenant_id, name) 联合唯一。*/
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "built_in", nullable = false)
    private Boolean builtIn = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<AdminPermission> permissions = new HashSet<>();
}
