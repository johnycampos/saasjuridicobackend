package com.jurisflow.modules.googlecalendar;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantGoogleCalendarRepository extends JpaRepository<TenantGoogleCalendar, UUID> {

    Optional<TenantGoogleCalendar> findByTenantId(UUID tenantId);

    Optional<TenantGoogleCalendar> findByTenantIdAndStatus(UUID tenantId, String status);

    List<TenantGoogleCalendar> findByStatusAndTokenExpiryBefore(String status, OffsetDateTime before, Pageable pageable);
}
