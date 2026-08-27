package com.jurisflow.modules.movimento;

import com.jurisflow.modules.movimento.dto.MovimentoResponse;
import com.jurisflow.modules.processo.ProcessoRepository;
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
public class MovimentoService {

    private final MovimentoRepository movimentoRepository;
    private final ProcessoRepository processoRepository;

    public List<MovimentoResponse> listByProcesso(UUID processoId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        validateProcessoInTenant(processoId, tenantId);
        return movimentoRepository.findByProcessoIdOrderByDataHoraDesc(processoId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public MovimentoResponse toggleVisualizado(UUID id, boolean visualizado, UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Movimento movimento = movimentoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Movimentacao"));

        movimento.setVisualizado(visualizado);
        movimento.setVisualizadoEm(visualizado ? LocalDateTime.now() : null);
        movimento.setVisualizadoPor(visualizado ? principal.getId() : null);

        return toResponse(movimentoRepository.save(movimento));
    }

    public Map<UUID, MovimentoResumo> resumoPorProcesso(Collection<UUID> processoIds) {
        if (processoIds.isEmpty()) return Map.of();

        return movimentoRepository.findByProcessoIdIn(processoIds).stream()
                .collect(Collectors.groupingBy(Movimento::getProcessoId))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    var movs = e.getValue();
                    var ultima = movs.stream().map(Movimento::getDataHora).filter(Objects::nonNull)
                            .max(Comparator.naturalOrder()).orElse(null);
                    var naoLida = movs.stream().anyMatch(m -> !Boolean.TRUE.equals(m.getVisualizado()));
                    return new MovimentoResumo(ultima, naoLida);
                }));
    }

    private void validateProcessoInTenant(UUID processoId, UUID tenantId) {
        processoRepository.findByIdAndTenantId(processoId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Processo"));
    }

    private MovimentoResponse toResponse(Movimento m) {
        return new MovimentoResponse(m.getId(), m.getTenantId(), m.getProcessoId(),
                m.getCodigo(), m.getNome(), m.getDataHora(), m.getDadosExtras(),
                m.getVisualizado(), m.getVisualizadoPor(), m.getVisualizadoEm(), m.getCreatedAt());
    }
}
