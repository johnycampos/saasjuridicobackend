package com.jurisflow.modules.processo;

import com.jurisflow.modules.cliente.ClienteRepository;
import com.jurisflow.modules.processo.dto.MoveProcessoRequest;
import com.jurisflow.modules.processo.dto.ProcessoRequest;
import com.jurisflow.modules.processo.dto.ProcessoResponse;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProcessoService {

    private final ProcessoRepository processoRepository;
    private final ClienteRepository clienteRepository;

    @Transactional
    public ProcessoResponse create(ProcessoRequest request, UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        validateClienteInTenant(request.clienteId(), tenantId);

        Processo processo = new Processo();
        processo.setTenantId(tenantId);
        processo.setClienteId(request.clienteId());
        processo.setDescricao(request.descricao());
        processo.setNumeroProcesso(request.numeroProcesso());
        processo.setTipoAcao(request.tipoAcao());
        processo.setVara(request.vara());
        processo.setComarca(request.comarca());
        processo.setTribunal(request.tribunal());
        processo.setReu(request.reu());
        processo.setPrioridade(request.prioridade() != null ? request.prioridade() : PrioridadeTipo.MEDIA);
        processo.setValorCausa(request.valorCausa());
        processo.setDataDistribuicao(request.dataDistribuicao());
        processo.setPrazoProximo(request.prazoProximo());
        processo.setGroupId(request.groupId());
        processo.setColumnId(request.columnId());
        processo.setCreatedBy(principal.getId());

        return toResponse(processoRepository.save(processo));
    }

    public Page<ProcessoResponse> listByTenant(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return processoRepository.findByTenantIdAndStatus(tenantId, ProcessoStatus.ATIVO, pageable)
                .map(this::toResponse);
    }

    public Page<ProcessoResponse> listByGroup(UUID groupId, Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return processoRepository.findByTenantIdAndGroupId(tenantId, groupId, pageable)
                .map(this::toResponse);
    }

    public Page<ProcessoResponse> search(String q, Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return processoRepository.search(tenantId, q, pageable).map(this::toResponse);
    }

    public ProcessoResponse getById(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return processoRepository.findByIdAndTenantId(id, tenantId)
                .map(this::toResponse)
                .orElseThrow(() -> BusinessException.notFound("Processo"));
    }

    @Transactional
    public ProcessoResponse update(UUID id, ProcessoRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Processo processo = processoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Processo"));
        validateClienteInTenant(request.clienteId(), tenantId);

        processo.setClienteId(request.clienteId());
        processo.setDescricao(request.descricao());
        processo.setNumeroProcesso(request.numeroProcesso());
        processo.setTipoAcao(request.tipoAcao());
        processo.setVara(request.vara());
        processo.setComarca(request.comarca());
        processo.setTribunal(request.tribunal());
        processo.setReu(request.reu());
        if (request.prioridade() != null) processo.setPrioridade(request.prioridade());
        processo.setValorCausa(request.valorCausa());
        processo.setDataDistribuicao(request.dataDistribuicao());
        processo.setPrazoProximo(request.prazoProximo());

        return toResponse(processoRepository.save(processo));
    }

    @Transactional
    public ProcessoResponse move(UUID id, MoveProcessoRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Processo processo = processoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Processo"));

        processo.setColumnId(request.targetColumnId());
        if (request.newPosition() != null) processo.setPosicaoColuna(request.newPosition());

        return toResponse(processoRepository.save(processo));
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Processo processo = processoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Processo"));
        processo.setStatus(ProcessoStatus.ARQUIVADO);
        processoRepository.save(processo);
    }

    private void validateClienteInTenant(UUID clienteId, UUID tenantId) {
        if (clienteId == null) return;
        clienteRepository.findByIdAndTenantId(clienteId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Cliente"));
    }

    private ProcessoResponse toResponse(Processo p) {
        String clienteNome = p.getClienteId() != null
                ? clienteRepository.findById(p.getClienteId()).map(c -> c.getNome()).orElse(null)
                : null;

        return new ProcessoResponse(
                p.getId(), p.getTenantId(), p.getGroupId(), p.getColumnId(),
                p.getClienteId(), clienteNome,
                p.getDescricao(), p.getNumeroProcesso(), p.getTipoAcao(),
                p.getVara(), p.getComarca(), p.getTribunal(), p.getReu(),
                p.getPrioridade(), p.getStatus(), p.getValorCausa(), p.getDataDistribuicao(),
                p.getPrazoProximo(), p.getPosicaoColuna(), p.getCreatedBy(),
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
