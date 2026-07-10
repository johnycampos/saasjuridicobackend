package com.jurisflow.modules.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContratoRepository extends JpaRepository<Contrato, UUID> {
    Optional<Contrato> findByProcessoIdAndTenantId(UUID processoId, UUID tenantId);
    Optional<Contrato> findByIdAndTenantId(UUID id, UUID tenantId);
    List<Contrato> findByProcessoIdInAndTenantId(Collection<UUID> processoIds, UUID tenantId);
}
