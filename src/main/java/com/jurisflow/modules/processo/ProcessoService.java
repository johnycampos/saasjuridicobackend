package com.jurisflow.modules.processo;

import com.jurisflow.modules.cliente.Cliente;
import com.jurisflow.modules.cliente.ClienteRepository;
import com.jurisflow.modules.group.GroupService;
import com.jurisflow.modules.processo.dto.MoveProcessoRequest;
import com.jurisflow.modules.processo.dto.ProcessoRequest;
import com.jurisflow.modules.processo.dto.ProcessoResponse;
import com.jurisflow.modules.tarefa.TarefaResumo;
import com.jurisflow.modules.tarefa.TarefaService;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProcessoService {

    private final ProcessoRepository processoRepository;
    private final ClienteRepository clienteRepository;
    private final TarefaService tarefaService;
    private final GroupService groupService;

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
        processo.setEstado(request.estado());
        processo.setTribunal(request.tribunal());
        processo.setReu(request.reu());
        processo.setValorCausa(request.valorCausa());
        processo.setDataDistribuicao(request.dataDistribuicao());
        processo.setGroupId(request.groupId());
        processo.setColumnId(request.columnId());
        processo.setCreatedBy(principal.getId());

        return toResponse(processoRepository.save(processo));
    }

    public Page<ProcessoResponse> listByTenant(Pageable pageable, UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        var restriction = groupService.resolveGroupRestriction(tenantId, userId);

        Page<Processo> page = restriction.isEmpty()
                ? processoRepository.findByTenantIdAndStatus(tenantId, ProcessoStatus.ATIVO, pageable)
                : processoRepository.findByTenantIdAndStatusAndGroupIdIn(tenantId, ProcessoStatus.ATIVO, restriction.get(), pageable);

        return toResponsePage(page);
    }

    public Page<ProcessoResponse> listByGroup(UUID groupId, Pageable pageable, UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        requireGroupAccess(tenantId, userId, groupId);
        return toResponsePage(processoRepository.findByTenantIdAndGroupId(tenantId, groupId, pageable));
    }

    public Page<ProcessoResponse> search(String q, Pageable pageable, UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        var restriction = groupService.resolveGroupRestriction(tenantId, userId);

        Page<Processo> page = restriction.isEmpty()
                ? processoRepository.search(tenantId, q, pageable)
                : processoRepository.searchInGroups(tenantId, q, restriction.get(), pageable);

        return toResponsePage(page);
    }

    public ProcessoResponse getById(UUID id, UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Processo processo = processoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Processo"));
        requireGroupAccess(tenantId, userId, processo.getGroupId());
        return toResponse(processo);
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
        processo.setEstado(request.estado());
        processo.setTribunal(request.tribunal());
        processo.setReu(request.reu());
        processo.setValorCausa(request.valorCausa());
        processo.setDataDistribuicao(request.dataDistribuicao());

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

    private void requireGroupAccess(UUID tenantId, UUID userId, UUID groupId) {
        var restriction = groupService.resolveGroupRestriction(tenantId, userId);
        if (restriction.isPresent() && !restriction.get().contains(groupId)) {
            throw BusinessException.forbidden();
        }
    }

    private void validateClienteInTenant(UUID clienteId, UUID tenantId) {
        if (clienteId == null) return;
        clienteRepository.findByIdAndTenantId(clienteId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Cliente"));
    }

    private ProcessoResponse toResponse(Processo p) {
        String clienteNome = resolveClienteNome(p.getClienteId());
        TarefaResumo resumo = tarefaService.resumoPorProcesso(List.of(p.getId()))
                .getOrDefault(p.getId(), TarefaResumo.VAZIO);
        return build(p, clienteNome, resumo);
    }

    private Page<ProcessoResponse> toResponsePage(Page<Processo> page) {
        List<Processo> content = page.getContent();

        var clienteIds = content.stream().map(Processo::getClienteId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> clienteNomes = clienteRepository.findAllById(clienteIds).stream()
                .collect(Collectors.toMap(Cliente::getId, Cliente::getNome));

        var resumos = tarefaService.resumoPorProcesso(content.stream().map(Processo::getId).toList());

        return page.map(p -> build(p, clienteNomes.get(p.getClienteId()), resumos.getOrDefault(p.getId(), TarefaResumo.VAZIO)));
    }

    private String resolveClienteNome(UUID clienteId) {
        return clienteId != null
                ? clienteRepository.findById(clienteId).map(Cliente::getNome).orElse(null)
                : null;
    }

    private ProcessoResponse build(Processo p, String clienteNome, TarefaResumo resumo) {
        return new ProcessoResponse(
                p.getId(), p.getTenantId(), p.getGroupId(), p.getColumnId(),
                p.getClienteId(), clienteNome,
                p.getDescricao(), p.getNumeroProcesso(), p.getTipoAcao(),
                p.getVara(), p.getComarca(), p.getEstado(), p.getTribunal(), p.getReu(),
                p.getStatus(), p.getValorCausa(), p.getDataDistribuicao(),
                resumo.prazo(), resumo.prioridade(),
                p.getPosicaoColuna(), p.getCreatedBy(),
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
