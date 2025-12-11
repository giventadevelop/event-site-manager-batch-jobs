package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantSettingsRepository extends JpaRepository<TenantSettings, String> {
    Optional<TenantSettings> findByTenantId(String tenantId);
}

