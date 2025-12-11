package com.eventmanager.batch.domain.enumeration;

/**
 * The EmailStatus enumeration.
 * Status of email delivery: SENT (successful), FAILED (delivery failed), BOUNCED (recipient rejected)
 */
public enum EmailStatus {
    SENT,
    FAILED,
    BOUNCED
}

