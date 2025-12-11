package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.EventAttendee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventAttendeeRepository extends JpaRepository<EventAttendee, Long> {
    @Query("SELECT DISTINCT e.email FROM EventAttendee e " +
           "WHERE e.eventId = :eventId " +
           "AND e.email IS NOT NULL " +
           "AND e.email != '' " +
           "AND e.registrationStatus = 'CONFIRMED'")
    List<String> findConfirmedEmailsByEventId(@Param("eventId") Long eventId);
}

