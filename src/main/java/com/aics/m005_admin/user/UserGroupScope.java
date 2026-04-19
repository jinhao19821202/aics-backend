package com.aics.m005_admin.user;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;

@Data
@Entity
@Table(name = "user_group_scope")
@IdClass(UserGroupScope.PK.class)
public class UserGroupScope {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "group_id")
    private String groupId;

    @Data
    public static class PK implements Serializable {
        private Long userId;
        private String groupId;
    }
}
