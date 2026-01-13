package com.eventmanager.batch.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;

/**
 * Entity representing payment provider configuration.
 * Used to retrieve Stripe API keys per tenant.
 */
@Entity
@Table(name = "payment_provider_config")
@Data
public class PaymentProviderConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "provider_name", nullable = false, length = 50)
    private String provider; // e.g., "STRIPE" - mapped to provider_name column

    @Column(name = "provider_secret_key_encrypted", columnDefinition = "text")
    private String providerSecretKeyEncrypted; // Encrypted provider secret key (AES-256-GCM)
}




