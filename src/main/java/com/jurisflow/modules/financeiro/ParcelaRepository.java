package com.jurisflow.modules.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParcelaRepository extends JpaRepository<Parcela, UUID> {
    List<Parcela> findByContratoIdOrderByNumeroAsc(UUID contratoId);
    List<Parcela> findByContratoIdInOrderByNumeroAsc(Collection<UUID> contratoIds);
    Optional<Parcela> findByIdAndTenantId(UUID id, UUID tenantId);
}
