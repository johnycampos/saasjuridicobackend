package com.jurisflow.modules.board;

import com.jurisflow.modules.board.dto.BoardColumnRequest;
import com.jurisflow.modules.board.dto.BoardColumnResponse;
import com.jurisflow.modules.board.dto.ReorderRequest;
import com.jurisflow.modules.cliente.Cliente;
import com.jurisflow.modules.cliente.ClienteRepository;
import com.jurisflow.modules.group.GroupService;
import com.jurisflow.modules.movimento.MovimentoResumo;
import com.jurisflow.modules.movimento.MovimentoService;
import com.jurisflow.modules.processo.Processo;
import com.jurisflow.modules.processo.ProcessoRepository;
import com.jurisflow.modules.processo.dto.ProcessoResponse;
import com.jurisflow.modules.tarefa.TarefaResumo;
import com.jurisflow.modules.tarefa.TarefaService;
import com.jurisflow.modules.tenant.TenantRole;
import com.jurisflow.security.TenantAccessGuard;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

    private final BoardColumnRepository columnRepository;
    private final ProcessoRepository processoRepository;
    private final ClienteRepository clienteRepository;
    private final TarefaService tarefaService;
    private final MovimentoService movimentoService;
    private final GroupService groupService;
    private final TenantAccessGuard tenantAccessGuard;

    public List<BoardColumnResponse> getBoardByGroup(UUID groupId, UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        var restriction = groupService.resolveGroupRestriction(tenantId, userId);
        if (restriction.isPresent() && !restriction.get().contains(groupId)) {
            throw BusinessException.forbidden();
        }

        List<BoardColumn> cols = columnRepository.findByGroupIdOrderByPosicaoAsc(groupId);

        Map<UUID, List<Processo>> processosByColumn = new LinkedHashMap<>();
        for (BoardColumn col : cols) {
            processosByColumn.put(col.getId(), processoRepository.findByColumnIdOrderByPosicaoColunaAsc(col.getId()));
        }

        Set<UUID> clienteIds = processosByColumn.values().stream()
                .flatMap(List::stream)
                .map(Processo::getClienteId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Cliente> clientesPorId = clienteRepository.findAllById(clienteIds).stream()
                .collect(Collectors.toMap(Cliente::getId, c -> c));

        List<UUID> processoIds = processosByColumn.values().stream()
                .flatMap(List::stream).map(Processo::getId).toList();
        Map<UUID, TarefaResumo> resumos = tarefaService.resumoPorProcesso(processoIds);
        Map<UUID, MovimentoResumo> movResumos = movimentoService.resumoPorProcesso(processoIds);

        Comparator<Processo> porUrgencia = Comparator
                .comparing((Processo p) -> {
                    var prioridade = resumos.getOrDefault(p.getId(), TarefaResumo.VAZIO).prioridade();
                    return prioridade != null ? prioridade.ordinal() : -1;
                })
                .reversed()
                .thenComparing(p -> {
                    var prazo = resumos.getOrDefault(p.getId(), TarefaResumo.VAZIO).prazo();
                    return prazo != null ? prazo : LocalDate.MAX;
                });

        return cols.stream()
                .map(col -> {
                    var processos = processosByColumn.get(col.getId()).stream()
                            .sorted(porUrgencia)
                            .map(p -> {
                                var resumo = resumos.getOrDefault(p.getId(), TarefaResumo.VAZIO);
                                var movResumo = movResumos.getOrDefault(p.getId(), MovimentoResumo.VAZIO);
                                Cliente cliente = clientesPorId.get(p.getClienteId());
                                return new ProcessoResponse(
                                        p.getId(), p.getTenantId(), p.getGroupId(), p.getColumnId(),
                                        p.getClienteId(), cliente != null ? cliente.getNome() : null,
                                        cliente != null ? cliente.getTelefone() : null,
                                        p.getDescricao(), p.getNumeroProcesso(), p.getTipoAcao(),
                                        p.getVara(), p.getComarca(), p.getEstado(), p.getTribunal(), p.getReu(),
                                        p.getStatus(), p.getValorCausa(), p.getDataDistribuicao(),
                                        resumo.prazo(), resumo.prioridade(),
                                        movResumo.ultimaMovimentacao(), movResumo.naoLida(),
                                        p.getPosicaoColuna(), p.getCreatedBy(),
                                        p.getCreatedAt(), p.getUpdatedAt());
                            })
                            .toList();
                    return new BoardColumnResponse(col.getId(), col.getTenantId(), col.getGroupId(),
                            col.getNome(), col.getPosicao(), col.getCor(), processos);
                })
                .toList();
    }

    @Transactional
    public BoardColumnResponse createColumn(BoardColumnRequest request, UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        requireColumnWriteAccess(tenantId, principal.getId(), request.groupId());
        groupService.assertGroupInTenant(request.groupId(), tenantId);

        Integer maxPos = columnRepository.findMaxPosicaoByGroupId(request.groupId());
        int nextPos = maxPos != null ? maxPos + 1 : 0;

        BoardColumn column = new BoardColumn();
        column.setTenantId(tenantId);
        column.setGroupId(request.groupId());
        column.setNome(request.nome());
        column.setCor(request.cor() != null ? request.cor() : "#6B7280");
        column.setPosicao(request.posicao() != null ? request.posicao() : nextPos);
        column = columnRepository.save(column);

        return new BoardColumnResponse(column.getId(), column.getTenantId(), column.getGroupId(),
                column.getNome(), column.getPosicao(), column.getCor(), List.of());
    }

    @Transactional
    public BoardColumnResponse updateColumn(UUID columnId, BoardColumnRequest request, UserPrincipal principal) {
        BoardColumn column = findColumnInTenant(columnId);
        requireColumnWriteAccess(column.getTenantId(), principal.getId(), column.getGroupId());
        column.setNome(request.nome());
        if (request.cor() != null) column.setCor(request.cor());
        columnRepository.save(column);
        return new BoardColumnResponse(column.getId(), column.getTenantId(), column.getGroupId(),
                column.getNome(), column.getPosicao(), column.getCor(), List.of());
    }

    @Transactional
    public void deleteColumn(UUID columnId, UserPrincipal principal) {
        BoardColumn column = findColumnInTenant(columnId);
        requireColumnWriteAccess(column.getTenantId(), principal.getId(), column.getGroupId());
        columnRepository.delete(column);
    }

    @Transactional
    public void reorderColumns(ReorderRequest request, UserPrincipal principal) {
        for (int i = 0; i < request.columnIds().size(); i++) {
            BoardColumn column = findColumnInTenant(request.columnIds().get(i));
            requireColumnWriteAccess(column.getTenantId(), principal.getId(), column.getGroupId());
            column.setPosicao(i);
            columnRepository.save(column);
        }
    }

    /**
     * Exige que o usuario seja pelo menos MEMBER do tenant (bloqueia so
     * VIEWER) e, se nao for ADMIN/OWNER, que pertenca ao grupo da coluna —
     * reaproveita a mesma restricao ja usada na leitura do board
     * (resolveGroupRestriction), fechando o RBAC ausente aqui.
     */
    private void requireColumnWriteAccess(UUID tenantId, UUID userId, UUID groupId) {
        tenantAccessGuard.requireMinRole(tenantId, userId, TenantRole.MEMBER);
        var restriction = groupService.resolveGroupRestriction(tenantId, userId);
        if (restriction.isPresent() && !restriction.get().contains(groupId)) {
            throw BusinessException.forbidden();
        }
    }

    private BoardColumn findColumnInTenant(UUID columnId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        BoardColumn column = columnRepository.findById(columnId)
                .orElseThrow(() -> BusinessException.notFound("Coluna"));
        if (!column.getTenantId().equals(tenantId)) throw BusinessException.forbidden();
        return column;
    }
}
