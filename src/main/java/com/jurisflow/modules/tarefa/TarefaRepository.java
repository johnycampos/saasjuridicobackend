package com.jurisflow.modules.tarefa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TarefaRepository extends JpaRepository<Tarefa, UUID> {
    List<Tarefa> findByProcessoIdOrderByConcluidaAscPrazoAsc(UUID processoId);
    List<Tarefa> findByProcessoIdInAndConcluidaFalse(Collection<UUID> processoIds);
    Optional<Tarefa> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
        SELECT t FROM Tarefa t
        WHERE t.tenantId = :tenantId
        AND t.concluida = false
        AND t.prazo BETWEEN :inicio AND :fim
        ORDER BY t.prazo ASC
    """)
    List<Tarefa> findAgendaSemana(UUID tenantId, LocalDate inicio, LocalDate fim);

    @Query("""
        SELECT t FROM Tarefa t
        WHERE t.tenantId = :tenantId
        AND t.concluida = false
        AND t.prazo BETWEEN :inicio AND :fim
        AND t.processoId IN (
            SELECT p.id FROM Processo p WHERE p.tenantId = :tenantId AND p.groupId IN :groupIds
        )
        ORDER BY t.prazo ASC
    """)
    List<Tarefa> findAgendaSemanaInGroups(UUID tenantId, LocalDate inicio, LocalDate fim, Collection<UUID> groupIds);
}
