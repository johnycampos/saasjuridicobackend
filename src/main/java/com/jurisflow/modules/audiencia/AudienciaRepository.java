package com.jurisflow.modules.audiencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AudienciaRepository extends JpaRepository<Audiencia, UUID> {

    Optional<Audiencia> findByIdAndTenantId(UUID id, UUID tenantId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Audiencia a WHERE a.id = :id AND a.tenantId = :tenantId")
    Optional<Audiencia> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("""
        SELECT a FROM Audiencia a WHERE a.tenantId = :tenantId AND a.status <> :status
        AND a.processoId IN (SELECT p.id FROM Processo p WHERE p.tenantId = :tenantId AND p.status = com.jurisflow.modules.processo.ProcessoStatus.ATIVO)
        ORDER BY a.data ASC, a.hora ASC
        """)
    List<Audiencia> findByTenantIdAndStatusNotOrderByDataAscHoraAsc(UUID tenantId, String status);

    List<Audiencia> findByProcessoIdAndTenantIdAndStatusNotOrderByDataAscHoraAsc(UUID processoId, UUID tenantId, String status);

    @Query("""
        SELECT a FROM Audiencia a
        WHERE a.tenantId = :tenantId
        AND a.processoId IN (SELECT p.id FROM Processo p WHERE p.tenantId = :tenantId AND p.status = com.jurisflow.modules.processo.ProcessoStatus.ATIVO)
        AND a.data BETWEEN :inicio AND :fim
        AND a.status <> 'CANCELADA'
        ORDER BY a.data ASC, a.hora ASC NULLS LAST
    """)
    List<Audiencia> findByTenantIdAndDataBetween(
            @Param("tenantId") UUID tenantId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim
    );

    @Query("""
        SELECT a FROM Audiencia a
        WHERE a.tenantId = :tenantId
        AND a.data BETWEEN :inicio AND :fim
        AND a.status <> 'CANCELADA'
        AND a.processoId IN (
            SELECT p.id FROM Processo p WHERE p.tenantId = :tenantId AND p.status = com.jurisflow.modules.processo.ProcessoStatus.ATIVO AND p.groupId IN :groupIds
        )
        ORDER BY a.data ASC, a.hora ASC NULLS LAST
    """)
    List<Audiencia> findByTenantIdAndDataBetweenInGroups(
            @Param("tenantId") UUID tenantId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("groupIds") Collection<UUID> groupIds
    );
}
