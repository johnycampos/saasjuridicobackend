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
    List<Movimento> findByTenantIdAndProcessoIdOrderByDataHoraDesc(UUID tenantId, UUID processoId, org.springframework.data.domain.Pageable pageable);
    Optional<Movimento> findByIdAndTenantId(UUID id, UUID tenantId);
    List<Movimento> findByProcessoIdIn(Collection<UUID> processoIds);

    interface ResumoProjection {
        UUID getProcessoId();
        java.time.LocalDateTime getUltimaMovimentacao();
        Boolean getNaoLida();
    }

    @org.springframework.data.jpa.repository.Query(value = """
        SELECT processo_id AS "processoId", MAX(data_hora) AS "ultimaMovimentacao",
               BOOL_OR(visualizado IS DISTINCT FROM TRUE) AS "naoLida"
        FROM processo_movimentos
        WHERE processo_id IN (:processoIds)
        GROUP BY processo_id
        """, nativeQuery = true)
    List<ResumoProjection> aggregateResumoPorProcesso(
        @org.springframework.data.repository.query.Param("processoIds") Collection<UUID> processoIds);
}
