package com.jurisflow.modules.audiencia;

import com.jurisflow.modules.audiencia.dto.AudienciaRequest;
import com.jurisflow.modules.audiencia.dto.AudienciaResponse;
import com.jurisflow.modules.cliente.Cliente;
import com.jurisflow.modules.cliente.ClienteRepository;
import com.jurisflow.modules.processo.Processo;
import com.jurisflow.modules.processo.ProcessoRepository;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AudienciaService {

    private final AudienciaRepository audienciaRepository;
    private final ProcessoRepository processoRepository;
    private final ClienteRepository clienteRepository;

    @Transactional
    public AudienciaResponse create(AudienciaRequest request, UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Processo processo = validateProcessoInTenant(request.processoId(), tenantId);

        Audiencia audiencia = Audiencia.builder()
                .tenantId(tenantId)
                .processoId(request.processoId())
                .titulo(request.titulo())
                .tipo(request.tipo() != null && !request.tipo().isBlank() ? request.tipo() : "AUDIENCIA")
                .data(request.data())
                .hora(request.hora())
                .vara(request.vara())
                .observacoes(request.observacoes())
                .status("AGENDADA")
                .createdBy(principal.getId())
                .build();

        Audiencia saved = audienciaRepository.save(audiencia);
        String clienteNome = buscarClienteNome(processo.getClienteId());
        return toResponse(saved, processo.getNumeroProcesso(), clienteNome);
    }

    @Transactional
    public AudienciaResponse update(UUID id, AudienciaRequest request, UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Audiencia audiencia = audienciaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Audiência"));

        Processo processo = validateProcessoInTenant(request.processoId(), tenantId);

        audiencia.setProcessoId(request.processoId());
        audiencia.setTitulo(request.titulo());
        if (request.tipo() != null && !request.tipo().isBlank()) {
            audiencia.setTipo(request.tipo());
        }
        audiencia.setData(request.data());
        audiencia.setHora(request.hora());
        audiencia.setVara(request.vara());
        audiencia.setObservacoes(request.observacoes());

        Audiencia updated = audienciaRepository.save(audiencia);
        String clienteNome = buscarClienteNome(processo.getClienteId());
        return toResponse(updated, processo.getNumeroProcesso(), clienteNome);
    }

    @Transactional
    public void delete(UUID id, UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Audiencia audiencia = audienciaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Audiência"));

        audiencia.setStatus("CANCELADA");
        audienciaRepository.save(audiencia);
    }

    public AudienciaResponse findById(UUID id, UUID tenantId) {
        Audiencia audiencia = audienciaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Audiência"));

        Processo processo = processoRepository.findByIdAndTenantId(audiencia.getProcessoId(), tenantId).orElse(null);
        String numeroProcesso = processo != null ? processo.getNumeroProcesso() : null;
        String clienteNome = processo != null ? buscarClienteNome(processo.getClienteId()) : null;

        return toResponse(audiencia, numeroProcesso, clienteNome);
    }

    public List<AudienciaResponse> list(UUID tenantId) {
        List<Audiencia> audiencias = audienciaRepository.findByTenantIdAndStatusNotOrderByDataAscHoraAsc(tenantId, "CANCELADA");
        return enrichAudiencias(audiencias, tenantId);
    }

    public List<AudienciaResponse> listByProcesso(UUID processoId, UUID tenantId) {
        validateProcessoInTenant(processoId, tenantId);
        List<Audiencia> audiencias = audienciaRepository.findByProcessoIdAndTenantIdAndStatusNotOrderByDataAscHoraAsc(processoId, tenantId, "CANCELADA");
        return enrichAudiencias(audiencias, tenantId);
    }

    public List<Audiencia> listByMes(UUID tenantId, LocalDate inicio, LocalDate fim, Optional<Set<UUID>> restriction) {
        return restriction.isEmpty()
                ? audienciaRepository.findByTenantIdAndDataBetween(tenantId, inicio, fim)
                : audienciaRepository.findByTenantIdAndDataBetweenInGroups(tenantId, inicio, fim, restriction.get());
    }

    private Processo validateProcessoInTenant(UUID processoId, UUID tenantId) {
        return processoRepository.findByIdAndTenantId(processoId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Processo"));
    }

    private String buscarClienteNome(UUID clienteId) {
        if (clienteId == null) return null;
        return clienteRepository.findById(clienteId).map(Cliente::getNome).orElse(null);
    }

    private List<AudienciaResponse> enrichAudiencias(List<Audiencia> audiencias, UUID tenantId) {
        if (audiencias.isEmpty()) return List.of();

        Set<UUID> processoIds = audiencias.stream().map(Audiencia::getProcessoId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Processo> processoMap = processoRepository.findAllById(processoIds).stream()
                .filter(p -> tenantId.equals(p.getTenantId())) // defense-in-depth
                .collect(Collectors.toMap(Processo::getId, p -> p));

        Set<UUID> clienteIds = processoMap.values().stream()
                .map(Processo::getClienteId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> clienteNomes = clienteRepository.findAllById(clienteIds).stream()
                .collect(Collectors.toMap(Cliente::getId, Cliente::getNome));

        return audiencias.stream().map(a -> {
            Processo p = processoMap.get(a.getProcessoId());
            String numero = p != null ? p.getNumeroProcesso() : null;
            String clienteNome = p != null && p.getClienteId() != null ? clienteNomes.get(p.getClienteId()) : null;
            return toResponse(a, numero, clienteNome);
        }).toList();
    }

    private AudienciaResponse toResponse(Audiencia a, String processoNumero, String clienteNome) {
        return new AudienciaResponse(
                a.getId(),
                a.getProcessoId(),
                processoNumero,
                clienteNome,
                a.getTitulo(),
                a.getTipo(),
                a.getData(),
                a.getHora(),
                a.getVara(),
                a.getObservacoes(),
                a.getStatus(),
                a.getCreatedBy(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}
