package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantSettingsRepository extends JpaRepository<TenantSettings, String> {
    Optional<TenantSettings> findByTenantId(String tenantId);

    /**
     * Get all distinct tenant IDs from tenant_settings table.
     * Used by scheduled batch job to process all tenants.
     */
    @Query("SELECT DISTINCT t.tenantId FROM TenantSettings t ORDER BY t.tenantId")
    List<String> findAllDistinctTenantIds();
}

