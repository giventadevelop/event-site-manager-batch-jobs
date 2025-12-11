package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.PromotionEmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromotionEmailTemplateRepository extends JpaRepository<PromotionEmailTemplate, Long> {
    Optional<PromotionEmailTemplate> findByIdAndTenantId(Long id, String tenantId);
}

