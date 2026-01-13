package com.eventmanager.batch.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * Entity representing an event ticket transaction.
 * This mirrors the event_ticket_transaction table from the main backend database.
 */
@Entity
@Table(name = "event_ticket_transaction")
@Data
public class EventTicketTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", length = 255)
    private String tenantId;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    @Column(name = "stripe_checkout_session_id", length = 255)
    private String stripeCheckoutSessionId;

    @Column(name = "status", nullable = false, length = 255)
    private String status;

    @Column(name = "final_amount", precision = 21, scale = 2)
    private BigDecimal finalAmount;

    @Column(name = "stripe_fee_amount", precision = 21, scale = 2)
    private BigDecimal stripeFeeAmount;

    @Column(name = "stripe_amount_tax", precision = 21, scale = 2)
    private BigDecimal stripeAmountTax;

    @Column(name = "net_payout_amount", precision = 21, scale = 2)
    private BigDecimal netPayoutAmount;

    @Column(name = "purchase_date", nullable = false)
    private ZonedDateTime purchaseDate;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;
}
