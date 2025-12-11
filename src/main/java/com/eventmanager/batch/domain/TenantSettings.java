package com.eventmanager.batch.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;

/**
 * Entity for tenant settings.
 * Simplified version for batch job processing.
 */
@Entity
@Table(name = "tenant_settings")
@Data
public class TenantSettings implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", length = 255, nullable = false, unique = true)
    private String tenantId;

    @Column(name = "email_footer_html_url", length = 2048)
    private String emailFooterHtmlUrl;

    @Column(name = "logo_image_url", length = 2048)
    private String logoImageUrl;
}

