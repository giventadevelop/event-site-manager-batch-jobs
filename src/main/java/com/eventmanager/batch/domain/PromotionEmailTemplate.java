package com.eventmanager.batch.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * Entity for promotion email templates.
 * Simplified version for batch job processing.
 */
@Entity
@Table(name = "promotion_email_template")
@Data
public class PromotionEmailTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", length = 255, nullable = false)
    private String tenantId;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "template_name", length = 255, nullable = false)
    private String templateName;

    @Column(name = "subject", length = 500, nullable = false)
    private String subject;

    @Column(name = "from_email", length = 255, nullable = false)
    private String fromEmail;

    @Column(name = "body_html", nullable = false, columnDefinition = "TEXT")
    private String bodyHtml;

    @Column(name = "footer_html", columnDefinition = "TEXT")
    private String footerHtml;

    @Column(name = "header_image_url", length = 2048)
    private String headerImageUrl;

    @Column(name = "footer_image_url", length = 2048)
    private String footerImageUrl;

    @Column(name = "promotion_code", length = 50)
    private String promotionCode;

    @Column(name = "discount_code_id")
    private Long discountCodeId;

    @Column(name = "is_active")
    private Boolean isActive;
}

