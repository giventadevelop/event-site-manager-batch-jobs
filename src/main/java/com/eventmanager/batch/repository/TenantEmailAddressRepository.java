package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.TenantEmailAddress;
import com.eventmanager.batch.domain.enumeration.TenantEmailType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantEmailAddressRepository extends JpaRepository<TenantEmailAddress, Long> {

    Optional<TenantEmailAddress> findFirstByTenantIdAndEmailTypeAndIsActiveTrueOrderByIsDefaultDesc(
        String tenantId,
        TenantEmailType emailType
    );

    List<TenantEmailAddress> findByTenantIdAndEmailTypeAndIsActiveTrue(String tenantId, TenantEmailType emailType);

    Optional<TenantEmailAddress> findFirstByTenantIdAndIsDefaultTrueAndIsActiveTrueOrderByIdAsc(String tenantId);

    List<TenantEmailAddress> findByTenantIdAndIsActiveTrue(String tenantId);
}




