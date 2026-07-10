package com.jurisflow.modules.tarefa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TarefaRepository extends JpaRepository<Tarefa, UUID> {
    List<Tarefa> findByProcessoIdOrderByConcluidaAscPrazoAsc(UUID processoId);
    List<Tarefa> findByProcessoIdInAndConcluidaFalse(Collection<UUID> processoIds);
    Optional<Tarefa> findByIdAndTenantId(UUID id, UUID tenantId);
}
