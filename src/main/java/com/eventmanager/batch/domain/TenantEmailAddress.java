package com.eventmanager.batch.domain;

import com.eventmanager.batch.domain.enumeration.TenantEmailType;
import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * Entity representing tenant-level email addresses and copy-to configuration.
 *
 * This mirrors the backend TenantEmailAddress entity in a simplified form
 * so the batch jobs service can resolve FROM and COPY-TO addresses.
 */
@Entity
@Table(name = "tenant_email_addresses")
@Data
public class TenantEmailAddress implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "email_address", nullable = false, length = 255)
    private String emailAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", length = 50)
    private TenantEmailType emailType;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "is_default")
    private Boolean isDefault;

    @Column(name = "copy_to_email_address", length = 255)
    private String copyToEmailAddress;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}


