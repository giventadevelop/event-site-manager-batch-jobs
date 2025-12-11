package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.PromotionEmailSentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromotionEmailSentLogRepository extends JpaRepository<PromotionEmailSentLog, Long> {
}

