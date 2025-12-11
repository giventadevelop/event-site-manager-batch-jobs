package com.eventmanager.batch.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;

/**
 * Entity for event attendees.
 * Simplified version for batch job processing.
 */
@Entity
@Table(name = "event_attendee")
@Data
public class EventAttendee implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "tenant_id", length = 255)
    private String tenantId;

    @Column(name = "registration_status", length = 50, nullable = false)
    private String registrationStatus;
}

