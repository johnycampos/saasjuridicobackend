package com.jurisflow.modules.dashboard;

import com.jurisflow.modules.cliente.Cliente;
import com.jurisflow.modules.cliente.ClienteRepository;
import com.jurisflow.modules.dashboard.dto.AniversarianteResponse;
import com.jurisflow.modules.dashboard.dto.DashboardResumoResponse;
import com.jurisflow.modules.financeiro.Contrato;
import com.jurisflow.modules.financeiro.ContratoRepository;
import com.jurisflow.modules.financeiro.Parcela;
import com.jurisflow.modules.financeiro.ParcelaRepository;
import com.jurisflow.modules.financeiro.ParcelaStatus;
import com.jurisflow.modules.group.GroupService;
import com.jurisflow.modules.processo.Processo;
import com.jurisflow.modules.processo.ProcessoRepository;
import com.jurisflow.modules.processo.ProcessoStatus;
import com.jurisflow.modules.tarefa.TarefaResumo;
import com.jurisflow.modules.tarefa.TarefaService;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final ProcessoRepository processoRepository;
    private final ClienteRepository clienteRepository;
    private final ContratoRepository contratoRepository;
    private final ParcelaRepository parcelaRepository;
    private final TarefaService tarefaService;
    private final GroupService groupService;

    public DashboardResumoResponse getResumo(UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        var restriction = groupService.resolveGroupRestriction(tenantId, principal.getId());

        List<Processo> processos = restriction.isEmpty()
                ? processoRepository.findByTenantIdAndStatus(tenantId, ProcessoStatus.ATIVO, Pageable.unpaged()).getContent()
                : processoRepository.findByTenantIdAndStatusAndGroupIdIn(tenantId, ProcessoStatus.ATIVO, restriction.get(), Pageable.unpaged()).getContent();

        var processoIds = processos.stream().map(Processo::getId).toList();

        ProximoPrazo prazo = calcularProximoPrazo(processos, processoIds);
        BigDecimal valorPago = calcularValorPagoTotal(processoIds, tenantId);
        List<AniversarianteResponse> aniversariantes = calcularAniversariantesDoMes(tenantId);

        return new DashboardResumoResponse(
                prazo.processoId(), prazo.numeroProcesso(), prazo.clienteNome(), prazo.data(), valorPago,
                aniversariantes
        );
    }

    private List<AniversarianteResponse> calcularAniversariantesDoMes(UUID tenantId) {
        int mesAtual = LocalDate.now().getMonthValue();
        return clienteRepository.findAniversariantesDoMes(tenantId, mesAtual).stream()
                .map(c -> new AniversarianteResponse(c.getId(), c.getNome(), c.getTelefone(), c.getDataNascimento()))
                .toList();
    }

    private ProximoPrazo calcularProximoPrazo(List<Processo> processos, List<UUID> processoIds) {
        Map<UUID, TarefaResumo> resumos = tarefaService.resumoPorProcesso(processoIds);

        UUID processoId = null;
        LocalDate data = null;
        for (Processo p : processos) {
            TarefaResumo resumo = resumos.get(p.getId());
            if (resumo == null || resumo.prazo() == null) continue;
            if (data == null || resumo.prazo().isBefore(data)) {
                data = resumo.prazo();
                processoId = p.getId();
            }
        }

        if (processoId == null) return new ProximoPrazo(null, null, null, null);

        UUID finalId = processoId;
        Processo processo = processos.stream().filter(p -> p.getId().equals(finalId)).findFirst().orElse(null);
        String numero = processo != null ? processo.getNumeroProcesso() : null;
        String clienteNome = processo != null && processo.getClienteId() != null
                ? clienteRepository.findById(processo.getClienteId()).map(Cliente::getNome).orElse(null)
                : null;

        return new ProximoPrazo(processoId, numero, clienteNome, data);
    }

    private BigDecimal calcularValorPagoTotal(List<UUID> processoIds, UUID tenantId) {
        List<Contrato> contratos = contratoRepository.findByProcessoIdInAndTenantId(processoIds, tenantId);
        var contratoIds = contratos.stream().map(Contrato::getId).toList();
        Map<UUID, List<Parcela>> parcelasPorContrato = parcelaRepository.findByContratoIdInOrderByNumeroAsc(contratoIds)
                .stream().collect(Collectors.groupingBy(Parcela::getContratoId));

        BigDecimal total = BigDecimal.ZERO;
        for (Contrato contrato : contratos) {
            var parcelas = parcelasPorContrato.getOrDefault(contrato.getId(), List.of());
            boolean todasPagas = !parcelas.isEmpty() && parcelas.stream().allMatch(pc -> pc.getStatus() == ParcelaStatus.PAGO);
            if (todasPagas) {
                total = total.add(contrato.getValorTotal());
            }
        }
        return total;
    }

    private record ProximoPrazo(UUID processoId, String numeroProcesso, String clienteNome, LocalDate data) {}
}
