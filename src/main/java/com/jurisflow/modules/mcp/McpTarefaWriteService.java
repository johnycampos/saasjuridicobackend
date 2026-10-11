package com.jurisflow.modules.mcp;

import com.jurisflow.modules.group.GroupService;
import com.jurisflow.modules.mcp.server.McpCallContext;
import com.jurisflow.modules.processo.ProcessoRepository;
import com.jurisflow.modules.tarefa.TarefaRepository;
import com.jurisflow.modules.tarefa.TarefaService;
import com.jurisflow.modules.tarefa.dto.TarefaRequest;
import com.jurisflow.modules.tarefa.dto.TarefaResponse;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class McpTarefaWriteService {
    private final ProcessoRepository processos;
    private final GroupService groups;
    private final TarefaRepository repository;
    private final TarefaService tarefas;

    @Transactional
    public TarefaResponse create(UUID processoId, TarefaRequest request, McpCallContext ctx) {
        lockAndRequireArea(processoId, ctx);
        return tarefas.create(processoId, request, ctx.principal());
    }

    @Transactional
    public TarefaResponse toggleConcluida(UUID tarefaId, boolean concluida, McpCallContext ctx) {
        var tarefa = repository.findByIdAndTenantId(tarefaId, ctx.tenantId())
            .orElseThrow(() -> BusinessException.notFound("Tarefa"));
        lockAndRequireArea(tarefa.getProcessoId(), ctx);
        return tarefas.toggleConcluida(tarefaId, concluida, ctx.principal());
    }

    private void lockAndRequireArea(UUID processoId, McpCallContext ctx) {
        // The lock remains held through the nested TarefaService write until this
        // transaction commits, serializing against concurrent process area moves.
        var processo = processos.findByIdAndTenantIdForUpdate(processoId, ctx.tenantId())
            .orElseThrow(() -> BusinessException.notFound("Processo"));
        var restriction = groups.resolveGroupRestriction(ctx.tenantId(), ctx.userId());
        if (restriction.isPresent() && !restriction.get().contains(processo.getGroupId()))
            throw BusinessException.forbidden();
    }
}
