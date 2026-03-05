package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.EventFocusGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventFocusGroupRepository extends JpaRepository<EventFocusGroup, Long> {
}
