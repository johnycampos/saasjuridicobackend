package com.jurisflow.modules.agenda;

import com.jurisflow.modules.agenda.dto.AgendaCompletaResponse;
import com.jurisflow.modules.agenda.dto.AgendaEventoResponse;
import com.jurisflow.modules.agenda.dto.AgendaResumoResponse;
import com.jurisflow.modules.audiencia.Audiencia;
import com.jurisflow.modules.audiencia.AudienciaRepository;
import com.jurisflow.modules.cliente.Cliente;
import com.jurisflow.modules.cliente.ClienteRepository;
import com.jurisflow.modules.financeiro.Contrato;
import com.jurisflow.modules.financeiro.ContratoRepository;
import com.jurisflow.modules.financeiro.Parcela;
import com.jurisflow.modules.financeiro.ParcelaRepository;
import com.jurisflow.modules.googlecalendar.GoogleCalendarService;
import com.jurisflow.modules.googlecalendar.dto.GoogleCalendarEventResponse;
import com.jurisflow.modules.group.GroupService;
import com.jurisflow.modules.processo.PrioridadeTipo;
import com.jurisflow.modules.processo.Processo;
import com.jurisflow.modules.processo.ProcessoRepository;
import com.jurisflow.modules.tarefa.Tarefa;
import com.jurisflow.modules.tarefa.TarefaRepository;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgendaService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final TarefaRepository tarefaRepository;
    private final ParcelaRepository parcelaRepository;
    private final ContratoRepository contratoRepository;
    private final ClienteRepository clienteRepository;
    private final ProcessoRepository processoRepository;
    private final AudienciaRepository audienciaRepository;
    private final GoogleCalendarService googleCalendarService;
    private final GroupService groupService;

    private void validateAnoMes(int ano, int mes) {
        if (mes < 1 || mes > 12) {
            throw new BusinessException("Mês inválido: " + mes);
        }
        if (ano < 2000 || ano > 2100) {
            throw new BusinessException("Ano inválido: " + ano);
        }
    }

    public AgendaCompletaResponse getAgendaCompleta(int ano, int mes, UserPrincipal principal) {
        validateAnoMes(ano, mes);

        UUID tenantId = TenantContext.getCurrentTenantId();
        var restriction = groupService.resolveGroupRestriction(tenantId, principal.getId());

        LocalDate inicio = LocalDate.of(ano, mes, 1);
        LocalDate fim = inicio.withDayOfMonth(inicio.lengthOfMonth());
        LocalDate hoje = LocalDate.now();

        List<AgendaEventoResponse> eventos = new ArrayList<>();

        // 1. Tarefas do mês
        List<Tarefa> tarefas = restriction.isEmpty()
                ? tarefaRepository.findByTenantIdAndPrazoBetween(tenantId, inicio, fim)
                : tarefaRepository.findByTenantIdAndPrazoBetweenInGroups(tenantId, inicio, fim, restriction.get());

        Set<UUID> processoIds = tarefas.stream().map(Tarefa::getProcessoId).filter(Objects::nonNull).collect(Collectors.toSet());

        // 2. Parcelas pendentes do mês
        List<Parcela> parcelas = restriction.isEmpty()
                ? parcelaRepository.findPendentesByVencimentoBetween(tenantId, inicio, fim)
                : parcelaRepository.findPendentesByVencimentoBetweenInGroups(tenantId, inicio, fim, restriction.get());

        Set<UUID> contratoIds = parcelas.stream().map(Parcela::getContratoId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Contrato> contratoMap = contratoRepository.findAllById(contratoIds).stream()
                .collect(Collectors.toMap(Contrato::getId, c -> c));

        contratoMap.values().stream()
                .map(Contrato::getProcessoId)
                .filter(Objects::nonNull)
                .forEach(processoIds::add);

        // 3. Audiências do mês
        List<Audiencia> audiencias = restriction.isEmpty()
                ? audienciaRepository.findByTenantIdAndDataBetween(tenantId, inicio, fim)
                : audienciaRepository.findByTenantIdAndDataBetweenInGroups(tenantId, inicio, fim, restriction.get());

        audiencias.stream()
                .map(Audiencia::getProcessoId)
                .filter(Objects::nonNull)
                .forEach(processoIds::add);

        // Batch fetch de Processos e Clientes
        Map<UUID, Processo> processoMap = processoRepository.findAllById(processoIds).stream()
                .filter(p -> tenantId.equals(p.getTenantId())) // defense-in-depth
                .collect(Collectors.toMap(Processo::getId, p -> p));

        Set<UUID> clienteIds = processoMap.values().stream()
                .map(Processo::getClienteId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> clienteNomes = clienteRepository.findAllById(clienteIds).stream()
                .collect(Collectors.toMap(Cliente::getId, Cliente::getNome));

        // Mapear Tarefas
        int totalAtrasados = 0;
        int totalUrgentes = 0;

        for (Tarefa t : tarefas) {
            Processo p = processoMap.get(t.getProcessoId());
            String numero = p != null ? p.getNumeroProcesso() : null;
            String clienteNome = p != null && p.getClienteId() != null ? clienteNomes.get(p.getClienteId()) : null;
            String prioridade = t.getPrioridade() != null ? t.getPrioridade().name() : null;
            UUID grupoId = p != null ? p.getGroupId() : null;
            String horaPrazoStr = t.getHoraPrazo() != null ? t.getHoraPrazo().format(TIME_FORMATTER) : null;

            if (t.getPrazo() != null && t.getPrazo().isBefore(hoje)) {
                totalAtrasados++;
            }
            if (t.getPrioridade() == PrioridadeTipo.URGENTE) {
                totalUrgentes++;
            }

            eventos.add(new AgendaEventoResponse(
                    "TAREFA",
                    t.getId(),
                    t.getProcessoId(),
                    grupoId,
                    t.getTitulo(),
                    clienteNome,
                    numero,
                    null,
                    prioridade,
                    t.getPrazo(),
                    horaPrazoStr,
                    null,
                    null,
                    "orange"
            ));
        }

        // Mapear Parcelas
        for (Parcela p : parcelas) {
            Contrato c = contratoMap.get(p.getContratoId());
            Processo proc = c != null && c.getProcessoId() != null ? processoMap.get(c.getProcessoId()) : null;
            String numero = proc != null ? proc.getNumeroProcesso() : null;
            String clienteNome = proc != null && proc.getClienteId() != null ? clienteNomes.get(proc.getClienteId()) : null;
            UUID grupoId = proc != null ? proc.getGroupId() : null;
            String titulo = "Parcela #" + (p.getNumero() != null ? p.getNumero() : 1) + (p.getValor() != null ? " - R$ " + p.getValor() : "");

            eventos.add(new AgendaEventoResponse(
                    "PARCELA",
                    p.getId(),
                    proc != null ? proc.getId() : null,
                    grupoId,
                    titulo,
                    clienteNome,
                    numero,
                    p.getValor(),
                    null,
                    p.getVencimento(),
                    null,
                    null,
                    null,
                    "green"
            ));
        }

        // Mapear Audiências
        for (Audiencia aud : audiencias) {
            Processo p = processoMap.get(aud.getProcessoId());
            String numero = p != null ? p.getNumeroProcesso() : null;
            String clienteNome = p != null && p.getClienteId() != null ? clienteNomes.get(p.getClienteId()) : null;
            UUID grupoId = p != null ? p.getGroupId() : null;
            String horaStr = aud.getHora() != null ? aud.getHora().format(TIME_FORMATTER) : null;

            eventos.add(new AgendaEventoResponse(
                    "AUDIENCIA",
                    aud.getId(),
                    aud.getProcessoId(),
                    grupoId,
                    aud.getTitulo(),
                    clienteNome,
                    numero,
                    null,
                    null,
                    aud.getData(),
                    horaStr,
                    null,
                    null,
                    "#e53935"
            ));
        }

        // 4. Google Calendar (se conectado)
        int totalEventosGoogle = 0;
        try {
            List<GoogleCalendarEventResponse> gEvents = googleCalendarService.getEvents(
                    tenantId, inicio.atStartOfDay(), fim.plusDays(1).atStartOfDay());
            totalEventosGoogle = gEvents.size();

            for (GoogleCalendarEventResponse ge : gEvents) {
                LocalDate data = ge.start() != null ? ge.start().toLocalDate() : (ge.end() != null ? ge.end().toLocalDate() : null);
                if (data != null) {
                    boolean startIsMidnight = ge.start() != null && ge.start().getHour() == 0 && ge.start().getMinute() == 0;
                    boolean endIsMidnight = ge.end() != null && ge.end().getHour() == 0 && ge.end().getMinute() == 0;

                    String horaInicio = ge.start() != null && !startIsMidnight ? ge.start().format(TIME_FORMATTER) : null;
                    String horaFim = ge.end() != null && !endIsMidnight ? ge.end().format(TIME_FORMATTER) : null;
                    String titulo = ge.summary() != null && !ge.summary().isBlank() ? ge.summary() : "Compromisso Google";

                    eventos.add(new AgendaEventoResponse(
                            "GOOGLE_CALENDAR",
                            null,
                            null,
                            null,
                            titulo,
                            null,
                            null,
                            null,
                            null,
                            data,
                            horaInicio,
                            horaFim,
                            ge.htmlLink(),
                            "blue"
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("Google Calendar não disponível para tenant {}: {}", tenantId, e.getMessage());
        }

        // Ordenar tudo por data
        eventos.sort(Comparator.comparing(AgendaEventoResponse::data, Comparator.nullsLast(Comparator.naturalOrder())));

        AgendaResumoResponse resumo = new AgendaResumoResponse(
                tarefas.size(),
                parcelas.size(),
                audiencias.size(),
                totalEventosGoogle,
                totalAtrasados,
                totalUrgentes
        );

        return new AgendaCompletaResponse(eventos, resumo);
    }

    public List<AgendaEventoResponse> getEventosMes(int ano, int mes, UserPrincipal principal) {
        return getAgendaCompleta(ano, mes, principal).eventos();
    }

    public AgendaResumoResponse getResumoMes(int ano, int mes, UserPrincipal principal) {
        return getAgendaCompleta(ano, mes, principal).resumo();
    }
}
