package com.jurisflow.modules.mcp;

import com.jurisflow.modules.audiencia.AudienciaRepository;
import com.jurisflow.modules.financeiro.ContratoRepository;
import com.jurisflow.modules.financeiro.ContratoService;
import com.jurisflow.modules.financeiro.ParcelaRepository;
import com.jurisflow.modules.financeiro.dto.ContratoRequest;
import com.jurisflow.modules.financeiro.dto.ContratoResponse;
import com.jurisflow.modules.financeiro.dto.ParcelaRequest;
import com.jurisflow.modules.financeiro.dto.ParcelaResponse;
import com.jurisflow.modules.group.GroupService;
import com.jurisflow.modules.mcp.server.McpCallContext;
import com.jurisflow.modules.movimento.MovimentoService;
import com.jurisflow.modules.movimento.dto.MovimentoResponse;
import com.jurisflow.modules.processo.ProcessoRepository;
import com.jurisflow.modules.processo.ProcessoService;
import com.jurisflow.modules.tarefa.TarefaRepository;
import com.jurisflow.modules.tarefa.TarefaService;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class McpWriteService {
    private final ProcessoRepository processos;
    private final GroupService groups;
    private final ProcessoService processoService;
    private final TarefaRepository tarefaRepository;
    private final TarefaService tarefaService;
    private final AudienciaRepository audienciaRepository;
    private final ContratoRepository contratoRepository;
    private final ParcelaRepository parcelaRepository;
    private final ContratoService contratoService;
    private final MovimentoService movimentoService;

    @Transactional
    public MovimentoResponse adicionarMovimentacao(
            UUID processoId, String nome, LocalDateTime dataHora, String texto, McpCallContext ctx) {
        lockAndRequireArea(processoId, ctx);
        return movimentoService.adicionarManual(processoId, nome, dataHora, texto, ctx.principal());
    }

    @Transactional
    public void arquivarProcesso(UUID processoId, McpCallContext ctx) {
        lockAndRequireArea(processoId, ctx);
        processoService.archive(processoId, ctx.principal());
    }

    @Transactional
    public void excluirTarefa(UUID tarefaId, McpCallContext ctx) {
        var tarefa = tarefaRepository.findByIdAndTenantId(tarefaId, ctx.tenantId())
            .orElseThrow(() -> BusinessException.notFound("Tarefa"));
        lockAndRequireArea(tarefa.getProcessoId(), ctx);
        tarefaService.delete(tarefaId);
    }

    @Transactional
    public void cancelarAudiencia(UUID audienciaId, McpCallContext ctx) {
        // Trava da AUDIÊNCIA primeiro com PESSIMISTIC_WRITE
        var audiencia = audienciaRepository.findByIdAndTenantIdForUpdate(audienciaId, ctx.tenantId())
            .orElseThrow(() -> BusinessException.notFound("Audiência"));
        // Depois trava do PROCESSO dela e revalidação de área
        lockAndRequireArea(audiencia.getProcessoId(), ctx);
        // E cancelamento usando a entidade travada
        audiencia.setStatus("CANCELADA");
        audienciaRepository.save(audiencia);
    }

    @Transactional
    public ContratoResponse definirContrato(UUID processoId, ContratoRequest request, McpCallContext ctx) {
        lockAndRequireArea(processoId, ctx);
        return contratoService.createOrUpdate(processoId, request, ctx.principal());
    }

    @Transactional
    public ParcelaResponse adicionarParcelaAoProcesso(UUID processoId, ParcelaRequest request, McpCallContext ctx) {
        lockAndRequireArea(processoId, ctx);
        var contrato = contratoRepository.findByProcessoIdAndTenantId(processoId, ctx.tenantId())
            .orElseThrow(() -> new BusinessException("Defina o contrato antes"));
        return contratoService.addParcela(contrato.getId(), request, ctx.principal());
    }

    @Transactional
    public ParcelaResponse marcarParcelaPaga(UUID parcelaId, McpCallContext ctx) {
        var parcela = parcelaRepository.findByIdAndTenantId(parcelaId, ctx.tenantId())
            .orElseThrow(() -> BusinessException.notFound("Parcela"));
        var contrato = contratoRepository.findByIdAndTenantId(parcela.getContratoId(), ctx.tenantId())
            .orElseThrow(() -> BusinessException.notFound("Contrato"));
        lockAndRequireArea(contrato.getProcessoId(), ctx);
        return contratoService.marcarPaga(parcelaId, ctx.principal());
    }

    @Transactional
    public void excluirParcela(UUID parcelaId, McpCallContext ctx) {
        var parcela = parcelaRepository.findByIdAndTenantId(parcelaId, ctx.tenantId())
            .orElseThrow(() -> new BusinessException("Parcela ja paga ou inexistente"));
        var contrato = contratoRepository.findByIdAndTenantId(parcela.getContratoId(), ctx.tenantId())
            .orElseThrow(() -> BusinessException.notFound("Contrato"));
        lockAndRequireArea(contrato.getProcessoId(), ctx);
        contratoService.deleteParcela(parcelaId, ctx.principal());
    }

    public void lockAndRequireArea(UUID processoId, McpCallContext ctx) {
        var processo = processos.findByIdAndTenantIdForUpdate(processoId, ctx.tenantId())
            .orElseThrow(() -> BusinessException.notFound("Processo"));
        var restriction = groups.resolveGroupRestriction(ctx.tenantId(), ctx.userId());
        if (restriction.isPresent() && !restriction.get().contains(processo.getGroupId()))
            throw BusinessException.forbidden();
    }
}
