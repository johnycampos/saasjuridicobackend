package com.jurisflow.modules.movimento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MovimentoRepository extends JpaRepository<Movimento, UUID> {
    List<Movimento> findByProcessoIdOrderByDataHoraDesc(UUID processoId);
    Optional<Movimento> findByIdAndTenantId(UUID id, UUID tenantId);
    List<Movimento> findByProcessoIdIn(Collection<UUID> processoIds);
}
