package com.jurisflow.modules.board;

import com.jurisflow.modules.board.dto.BoardColumnRequest;
import com.jurisflow.modules.board.dto.BoardColumnResponse;
import com.jurisflow.modules.board.dto.ReorderRequest;
import com.jurisflow.modules.processo.ProcessoRepository;
import com.jurisflow.modules.processo.dto.ProcessoResponse;
import com.jurisflow.security.TenantContext;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

    private final BoardColumnRepository columnRepository;
    private final ProcessoRepository processoRepository;

    public List<BoardColumnResponse> getBoardByGroup(UUID groupId) {
        return columnRepository.findByGroupIdOrderByPosicaoAsc(groupId)
                .stream()
                .map(col -> {
                    var processos = processoRepository
                            .findByColumnIdOrderByPosicaoColunaAsc(col.getId())
                            .stream()
                            .map(p -> new ProcessoResponse(
                                    p.getId(), p.getTenantId(), p.getGroupId(), p.getColumnId(),
                                    p.getTitulo(), p.getDescricao(), p.getNumeroProcesso(), p.getTipoAcao(),
                                    p.getVara(), p.getComarca(), p.getTribunal(), p.getAutor(), p.getReu(),
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
