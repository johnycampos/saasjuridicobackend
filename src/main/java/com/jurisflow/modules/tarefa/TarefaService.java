package com.jurisflow.modules.tarefa;

import com.jurisflow.modules.processo.PrioridadeTipo;
import com.jurisflow.modules.processo.ProcessoRepository;
import com.jurisflow.modules.tarefa.dto.TarefaRequest;
import com.jurisflow.modules.tarefa.dto.TarefaResponse;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TarefaService {

    private final TarefaRepository tarefaRepository;
    private final ProcessoRepository processoRepository;

    @Transactional
    public TarefaResponse create(UUID processoId, TarefaRequest request, UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        validateProcessoInTenant(processoId, tenantId);

        Tarefa tarefa = new Tarefa();
        tarefa.setTenantId(tenantId);
        tarefa.setProcessoId(processoId);
        tarefa.setTitulo(request.titulo());
        tarefa.setDescricao(request.descricao());
        tarefa.setPrioridade(request.prioridade() != null ? request.prioridade() : PrioridadeTipo.MEDIA);
        tarefa.setPrazo(request.prazo());
        tarefa.setCreatedBy(principal.getId());

        return toResponse(tarefaRepository.save(tarefa));
    }

    public List<TarefaResponse> listByProcesso(UUID processoId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        validateProcessoInTenant(processoId, tenantId);
        return tarefaRepository.findByProcessoIdOrderByConcluidaAscPrazoAsc(processoId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public TarefaResponse toggleConcluida(UUID id, boolean concluida, UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Tarefa tarefa = tarefaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Tarefa"));

        tarefa.setConcluida(concluida);
        tarefa.setConcluidaEm(concluida ? LocalDateTime.now() : null);
        tarefa.setConcluidaPor(concluida ? principal.getId() : null);

        return toResponse(tarefaRepository.save(tarefa));
    }

    public Map<UUID, TarefaResumo> resumoPorProcesso(Collection<UUID> processoIds) {
        if (processoIds.isEmpty()) return Map.of();

        return tarefaRepository.findByProcessoIdInAndConcluidaFalse(processoIds).stream()
                .collect(Collectors.groupingBy(Tarefa::getProcessoId))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    var abertas = e.getValue();
                    var prazo = abertas.stream().map(Tarefa::getPrazo).filter(Objects::nonNull)
                            .min(Comparator.naturalOrder()).orElse(null);
                    var prioridade = abertas.stream().map(Tarefa::getPrioridade).filter(Objects::nonNull)
                            .max(Comparator.comparingInt(Enum::ordinal)).orElse(null);
                    return new TarefaResumo(prazo, prioridade);
                }));
    }

    private void validateProcessoInTenant(UUID processoId, UUID tenantId) {
        processoRepository.findByIdAndTenantId(processoId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Processo"));
    }

    private TarefaResponse toResponse(Tarefa t) {
        return new TarefaResponse(t.getId(), t.getTenantId(), t.getProcessoId(), t.getTitulo(),
                t.getDescricao(), t.getPrioridade(), t.getPrazo(), t.getConcluida(),
                t.getConcluidaEm(), t.getConcluidaPor(), t.getCreatedBy(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}
