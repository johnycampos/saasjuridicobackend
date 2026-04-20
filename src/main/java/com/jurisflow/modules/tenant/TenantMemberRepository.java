package com.jurisflow.modules.tenant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantMemberRepository extends JpaRepository<TenantMember, UUID> {
    Optional<TenantMember> findByTenantIdAndUser_Id(UUID tenantId, UUID userId);
    boolean existsByTenantIdAndUser_IdAndAtivoTrue(UUID tenantId, UUID userId);
    Page<TenantMember> findByTenantIdAndAtivoTrue(UUID tenantId, Pageable pageable);
    long countByTenantIdAndAtivoTrue(UUID tenantId);

    @Query("SELECT tm FROM TenantMember tm WHERE tm.tenantId = :tenantId AND tm.user.id = :userId AND tm.ativo = true")
    Optional<TenantMember> findActiveMember(UUID tenantId, UUID userId);

    Optional<TenantMember> findFirstByUser_IdAndAtivoTrue(UUID userId);
}
