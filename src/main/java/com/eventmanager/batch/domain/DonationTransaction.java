package com.eventmanager.batch.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * Entity representing a donation transaction.
 * This mirrors the donation_transaction table from the main backend database.
 */
@Entity
@Table(name = "donation_transaction")
@Data
public class DonationTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "payment_transaction_id")
    private Long paymentTransactionId;

    @Column(name = "transaction_reference", nullable = false, length = 255)
    private String transactionReference;

    @Column(name = "givebutter_donation_id", length = 255)
    private String givebutterDonationId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "first_name", length = 255)
    private String firstName;

    @Column(name = "last_name", length = 255)
    private String lastName;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "prayer_intention", columnDefinition = "text")
    private String prayerIntention;

    @Column(name = "is_recurring", nullable = false)
    private Boolean isRecurring = false;

    @Column(name = "is_anonymous", nullable = false)
    private Boolean isAnonymous = false;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "qr_code_url", columnDefinition = "text")
    private String qrCodeUrl;

    @Column(name = "qr_code_image_url", columnDefinition = "text")
    private String qrCodeImageUrl;

    @Column(name = "email_sent", nullable = false)
    private Boolean emailSent = false;

    @Column(name = "metadata", columnDefinition = "text")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
