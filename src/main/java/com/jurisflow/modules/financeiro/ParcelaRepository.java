package com.jurisflow.modules.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParcelaRepository extends JpaRepository<Parcela, UUID> {
    List<Parcela> findByContratoIdOrderByNumeroAsc(UUID contratoId);
    List<Parcela> findByContratoIdInOrderByNumeroAsc(Collection<UUID> contratoIds);
    Optional<Parcela> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
        SELECT p FROM Parcela p
        WHERE p.tenantId = :tenantId
        AND p.status = com.jurisflow.modules.financeiro.ParcelaStatus.PENDENTE
        AND p.vencimento BETWEEN :inicio AND :fim
        ORDER BY p.vencimento ASC
    """)
    List<Parcela> findPendentesByVencimentoBetween(UUID tenantId, LocalDate inicio, LocalDate fim);

    @Query("""
        SELECT p FROM Parcela p
        JOIN Contrato c ON p.contratoId = c.id
        WHERE p.tenantId = :tenantId
        AND p.status = com.jurisflow.modules.financeiro.ParcelaStatus.PENDENTE
        AND p.vencimento BETWEEN :inicio AND :fim
        AND c.processoId IN (
            SELECT pr.id FROM Processo pr WHERE pr.tenantId = :tenantId AND pr.groupId IN :groupIds
        )
        ORDER BY p.vencimento ASC
    """)
    List<Parcela> findPendentesByVencimentoBetweenInGroups(UUID tenantId, LocalDate inicio, LocalDate fim, Collection<UUID> groupIds);
}
