package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.EventMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventMediaRepository extends JpaRepository<EventMedia, Long> {
}
