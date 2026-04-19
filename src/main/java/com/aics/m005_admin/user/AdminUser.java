package com.aics.m005_admin.user;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "admin_user")
public class AdminUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    private String email;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = false;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<AdminRole> roles = new HashSet<>();
}
