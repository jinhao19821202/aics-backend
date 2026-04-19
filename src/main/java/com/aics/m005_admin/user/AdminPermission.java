package com.aics.m005_admin.user;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "admin_permission")
public class AdminPermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    private String description;
}
