package com.dams.appearance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Platform-wide key/value setting owned by Super Admin. Not org-scoped (no org_id) —
 * see AGENT.md "Appearance — platform font".
 */
@Entity
@Table(name = "platform_setting")
@Getter
@Setter
@NoArgsConstructor
public class PlatformSetting {

    @Id
    @Column(name = "key", length = 64)
    private String key;

    @Column(nullable = false)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private Long updatedBy;

    public PlatformSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
