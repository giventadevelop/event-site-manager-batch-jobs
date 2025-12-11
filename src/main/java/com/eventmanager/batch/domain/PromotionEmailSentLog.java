package com.eventmanager.batch.domain;

import com.eventmanager.batch.domain.enumeration.EmailStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * Entity for logging sent promotion emails.
 * Simplified version for batch job processing.
 */
@Entity
@Table(name = "promotion_email_sent_log")
@Data
public class PromotionEmailSentLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", length = 255, nullable = false)
    private String tenantId;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "recipient_email", length = 255, nullable = false)
    private String recipientEmail;

    @Column(name = "subject", length = 500, nullable = false)
    private String subject;

    @Column(name = "promotion_code", length = 50)
    private String promotionCode;

    @Column(name = "discount_code_id")
    private Long discountCodeId;

    @Column(name = "sent_at", nullable = false)
    private ZonedDateTime sentAt;

    @Column(name = "is_test_email")
    private Boolean isTestEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", length = 50, nullable = false)
    private EmailStatus emailStatus;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "sent_by_id")
    private Long sentById;
}

