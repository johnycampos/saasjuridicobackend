package com.jurisflow.modules.processo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    List<Processo> findByTenantIdAndClienteId(UUID tenantId, UUID clienteId);

    List<Processo> findByTenantIdAndClienteIdAndGroupIdIn(UUID tenantId, UUID clienteId, Collection<UUID> groupIds);

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

    // numero_processo eh VARCHAR livre (sem constraint unica, com ou sem
    // pontuacao CNJ) — usado pela sincronizacao em lote de movimentos
    // (com.jurisflow.modules.movimento.MovimentoService.syncProcesso), que so
    // recebe o numero do processo (nao o UUID interno), comparando so digitos
    // dos dois lados. Ver indice funcional na migration V21.
    @Query(value = """
        SELECT * FROM processos p
        WHERE p.tenant_id = :tenantId
        AND regexp_replace(p.numero_processo, '[^0-9]', '', 'g') = :numeroDigits
        """, nativeQuery = true)
    List<Processo> findByTenantIdAndNumeroProcessoDigits(@Param("tenantId") UUID tenantId, @Param("numeroDigits") String numeroDigits);

    // Usado por GET /api/integrations/processos (com.jurisflow.modules.processo.ProcessoSyncController),
    // que alimenta o script externo com os processos a consultar no DataJud.
    // Deliberadamente cross-tenant quando :tenantId eh null (nao usa
    // TenantContext) — ver nota no controller.
    @Query("""
        SELECT p FROM Processo p
        WHERE p.status = :status
        AND p.numeroProcesso IS NOT NULL AND TRIM(p.numeroProcesso) <> ''
        AND (:tenantId IS NULL OR p.tenantId = :tenantId)
        """)
    Page<Processo> findAtivosComNumeroProcesso(@Param("status") ProcessoStatus status,
                                                @Param("tenantId") UUID tenantId,
                                                Pageable pageable);
}
