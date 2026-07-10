package com.jurisflow.modules.board;

import com.jurisflow.modules.board.dto.BoardColumnRequest;
import com.jurisflow.modules.board.dto.BoardColumnResponse;
import com.jurisflow.modules.board.dto.ReorderRequest;
import com.jurisflow.modules.cliente.Cliente;
import com.jurisflow.modules.cliente.ClienteRepository;
import com.jurisflow.modules.processo.Processo;
import com.jurisflow.modules.processo.ProcessoRepository;
import com.jurisflow.modules.processo.dto.ProcessoResponse;
import com.jurisflow.security.TenantContext;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public List<BoardColumnResponse> getBoardByGroup(UUID groupId) {
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
        Map<UUID, String> clienteNomes = clienteRepository.findAllById(clienteIds).stream()
                .collect(Collectors.toMap(Cliente::getId, Cliente::getNome));

        return cols.stream()
                .map(col -> {
                    var processos = processosByColumn.get(col.getId())
                            .stream()
                            .map(p -> new ProcessoResponse(
                                    p.getId(), p.getTenantId(), p.getGroupId(), p.getColumnId(),
                                    p.getClienteId(), clienteNomes.get(p.getClienteId()),
                                    p.getDescricao(), p.getNumeroProcesso(), p.getTipoAcao(),
                                    p.getVara(), p.getComarca(), p.getTribunal(), p.getReu(),
                                    p.getPrioridade(), p.getStatus(), p.getValorCausa(), p.getDataDistribuicao(),
                                    p.getPrazoProximo(), p.getPosicaoColuna(), p.getCreatedBy(),
                                    p.getCreatedAt(), p.getUpdatedAt()))
                            .toList();
                    return new BoardColumnResponse(col.getId(), col.getTenantId(), col.getGroupId(),
                            col.getNome(), col.getPosicao(), col.getCor(), processos);
                })
                .toList();
    }

    @Transactional
    public BoardColumnResponse createColumn(BoardColumnRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
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
    public BoardColumnResponse updateColumn(UUID columnId, BoardColumnRequest request) {
        BoardColumn column = findColumnInTenant(columnId);
        column.setNome(request.nome());
        if (request.cor() != null) column.setCor(request.cor());
        columnRepository.save(column);
        return new BoardColumnResponse(column.getId(), column.getTenantId(), column.getGroupId(),
                column.getNome(), column.getPosicao(), column.getCor(), List.of());
    }

    @Transactional
    public void deleteColumn(UUID columnId) {
        columnRepository.delete(findColumnInTenant(columnId));
    }

    @Transactional
    public void reorderColumns(ReorderRequest request) {
        for (int i = 0; i < request.columnIds().size(); i++) {
            BoardColumn column = findColumnInTenant(request.columnIds().get(i));
            column.setPosicao(i);
            columnRepository.save(column);
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
