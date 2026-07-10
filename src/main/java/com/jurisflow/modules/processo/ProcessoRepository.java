package com.jurisflow.modules.processo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcessoRepository extends JpaRepository<Processo, UUID> {

    Page<Processo> findByTenantIdAndStatus(UUID tenantId, ProcessoStatus status, Pageable pageable);

    Page<Processo> findByTenantIdAndStatusAndGroupIdIn(UUID tenantId, ProcessoStatus status, Collection<UUID> groupIds, Pageable pageable);

    List<Processo> findByColumnIdOrderByPosicaoColunaAsc(UUID columnId);

    Page<Processo> findByTenantIdAndGroupId(UUID tenantId, UUID groupId, Pageable pageable);

    Optional<Processo> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByGroupIdAndStatus(UUID groupId, ProcessoStatus status);

    @Query("""
        SELECT p FROM Processo p
        WHERE p.tenantId = :tenantId
        AND (
            LOWER(p.numeroProcesso) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(p.reu) LIKE LOWER(CONCAT('%', :q, '%'))
            OR EXISTS (
                SELECT 1 FROM Cliente c
                WHERE c.id = p.clienteId AND LOWER(c.nome) LIKE LOWER(CONCAT('%', :q, '%'))
            )
        )
    """)
    Page<Processo> search(UUID tenantId, String q, Pageable pageable);

    @Query("""
        SELECT p FROM Processo p
        WHERE p.tenantId = :tenantId
        AND p.groupId IN :groupIds
        AND (
            LOWER(p.numeroProcesso) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(p.reu) LIKE LOWER(CONCAT('%', :q, '%'))
            OR EXISTS (
                SELECT 1 FROM Cliente c
                WHERE c.id = p.clienteId AND LOWER(c.nome) LIKE LOWER(CONCAT('%', :q, '%'))
            )
        )
    """)
    Page<Processo> searchInGroups(UUID tenantId, String q, Collection<UUID> groupIds, Pageable pageable);
}
