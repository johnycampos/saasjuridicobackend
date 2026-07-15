package com.jurisflow.modules.financeiro;

import com.jurisflow.modules.cliente.ClienteRepository;
import com.jurisflow.modules.financeiro.dto.ClienteProcessoFinanceiroResponse;
import com.jurisflow.modules.financeiro.dto.ContratoRequest;
import com.jurisflow.modules.financeiro.dto.ContratoResponse;
import com.jurisflow.modules.financeiro.dto.ParcelaRequest;
import com.jurisflow.modules.financeiro.dto.ParcelaResponse;
import com.jurisflow.modules.group.GroupService;
import com.jurisflow.modules.processo.Processo;
import com.jurisflow.modules.processo.ProcessoRepository;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContratoService {

    private final ContratoRepository contratoRepository;
    private final ParcelaRepository parcelaRepository;
    private final ProcessoRepository processoRepository;
    private final ClienteRepository clienteRepository;
    private final GroupService groupService;

    @Transactional
    public ContratoResponse createOrUpdate(UUID processoId, ContratoRequest request, UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Processo processo = findProcessoComAcesso(processoId, tenantId, principal.getId());

        Contrato contrato = contratoRepository.findByProcessoIdAndTenantId(processoId, tenantId)
                .orElseGet(() -> {
                    Contrato novo = new Contrato();
                    novo.setTenantId(tenantId);
                    novo.setProcessoId(processo.getId());
                    novo.setCreatedBy(principal.getId());
                    return novo;
                });
        contrato.setValorTotal(request.valorTotal());
        contrato.setObservacoes(request.observacoes());

        return toResponse(contratoRepository.save(contrato));
    }

    public ContratoResponse getByProcesso(UUID processoId, UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        findProcessoComAcesso(processoId, tenantId, userId);

        Contrato contrato = contratoRepository.findByProcessoIdAndTenantId(processoId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Contrato"));
        return toResponse(contrato);
    }

    @Transactional
    public ParcelaResponse addParcela(UUID contratoId, ParcelaRequest request, UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Contrato contrato = contratoRepository.findByIdAndTenantId(contratoId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Contrato"));
        findProcessoComAcesso(contrato.getProcessoId(), tenantId, principal.getId());

        Parcela parcela = new Parcela();
        parcela.setTenantId(tenantId);
        parcela.setContratoId(contratoId);
        parcela.setNumero(request.numero() != null ? request.numero() : proximoNumero(contratoId));
        parcela.setValor(request.valor());
        parcela.setVencimento(request.vencimento());
        parcela.setCreatedBy(principal.getId());

        return toParcelaResponse(parcelaRepository.save(parcela));
    }

    @Transactional
    public ParcelaResponse marcarPaga(UUID parcelaId, UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Parcela parcela = parcelaRepository.findByIdAndTenantId(parcelaId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Parcela"));
        Contrato contrato = contratoRepository.findByIdAndTenantId(parcela.getContratoId(), tenantId)
                .orElseThrow(() -> BusinessException.notFound("Contrato"));
        findProcessoComAcesso(contrato.getProcessoId(), tenantId, principal.getId());

        parcela.setStatus(ParcelaStatus.PAGO);
        parcela.setDataPagamento(LocalDateTime.now());
        parcela.setPagoPor(principal.getId());

        return toParcelaResponse(parcelaRepository.save(parcela));
    }

    public List<ClienteProcessoFinanceiroResponse> listProcessosDoCliente(UUID clienteId, UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        clienteRepository.findByIdAndTenantId(clienteId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Cliente"));

        var restriction = groupService.resolveGroupRestriction(tenantId, userId);
        List<Processo> processos = restriction.isEmpty()
                ? processoRepository.findByTenantIdAndClienteId(tenantId, clienteId)
                : processoRepository.findByTenantIdAndClienteIdAndGroupIdIn(tenantId, clienteId, restriction.get());

        var processoIds = processos.stream().map(Processo::getId).toList();
        Map<UUID, Contrato> contratosPorProcesso = contratoRepository.findByProcessoIdInAndTenantId(processoIds, tenantId)
                .stream().collect(Collectors.toMap(Contrato::getProcessoId, c -> c));

        var contratoIds = contratosPorProcesso.values().stream().map(Contrato::getId).toList();
        Map<UUID, List<Parcela>> parcelasPorContrato = parcelaRepository.findByContratoIdInOrderByNumeroAsc(contratoIds)
                .stream().collect(Collectors.groupingBy(Parcela::getContratoId));

        return processos.stream().map(p -> {
            Contrato contrato = contratosPorProcesso.get(p.getId());
            if (contrato == null) {
                return new ClienteProcessoFinanceiroResponse(p.getId(), p.getNumeroProcesso(), p.getTipoAcao(),
                        null, null, "SEM_CONTRATO", 0, 0);
            }
            var parcelas = parcelasPorContrato.getOrDefault(contrato.getId(), List.of());
            long pagas = parcelas.stream().filter(pc -> pc.getStatus() == ParcelaStatus.PAGO).count();
            String status = (!parcelas.isEmpty() && pagas == parcelas.size()) ? "PAGO" : "PENDENTE";
            return new ClienteProcessoFinanceiroResponse(p.getId(), p.getNumeroProcesso(), p.getTipoAcao(),
                    contrato.getId(), contrato.getValorTotal(), status, (int) pagas, parcelas.size());
        }).toList();
    }

    private Processo findProcessoComAcesso(UUID processoId, UUID tenantId, UUID userId) {
        Processo processo = processoRepository.findByIdAndTenantId(processoId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Processo"));

        var restriction = groupService.resolveGroupRestriction(tenantId, userId);
        if (restriction.isPresent() && !restriction.get().contains(processo.getGroupId())) {
            throw BusinessException.forbidden();
        }
        return processo;
    }

    private Integer proximoNumero(UUID contratoId) {
        return parcelaRepository.findByContratoIdOrderByNumeroAsc(contratoId).size() + 1;
    }

    private ContratoResponse toResponse(Contrato c) {
        List<Parcela> parcelasEntities = parcelaRepository.findByContratoIdOrderByNumeroAsc(c.getId());
        List<ParcelaResponse> parcelas = parcelasEntities.stream().map(this::toParcelaResponse).toList();

        BigDecimal totalPago = parcelasEntities.stream()
                .filter(p -> p.getStatus() == ParcelaStatus.PAGO)
                .map(Parcela::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saldoRestante = c.getValorTotal().subtract(totalPago);

        return new ContratoResponse(c.getId(), c.getTenantId(), c.getProcessoId(), c.getValorTotal(), saldoRestante,
                c.getObservacoes(), c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedAt(), parcelas);
    }

    private ParcelaResponse toParcelaResponse(Parcela p) {
        return new ParcelaResponse(p.getId(), p.getTenantId(), p.getContratoId(), p.getNumero(),
                p.getValor(), p.getVencimento(), p.getStatus(), p.getDataPagamento(), p.getPagoPor(),
                p.getCreatedBy(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
