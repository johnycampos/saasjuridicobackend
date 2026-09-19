package com.jurisflow.modules.agenda.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento unificado da agenda. Combina dados de múltiplas fontes:
 * - tipo "TAREFA": prazos de tarefas de processos (cor laranja)
 * - tipo "PARCELA": vencimentos de parcelas financeiras (cor verde)
 * - tipo "GOOGLE_CALENDAR": eventos do Google Calendar sincronizado (cor azul)
 * - tipo "AUDIENCIA": audiências/compromissos jurídicos (cor vermelha)
 *
 * @param horaInicio horário no formato "HH:mm" (preenchido para AUDIENCIA e tarefas com hora, null para dia inteiro)
 */
public record AgendaEventoResponse(
    String tipo,           // "TAREFA", "PARCELA", "GOOGLE_CALENDAR", "AUDIENCIA"
    UUID id,               // ID da entidade (null para Google Calendar)
    UUID processoId,       // ID do processo (null para parcela sem processo e Google Calendar)
    UUID grupoId,          // ID do grupo/área (via processo.getGroupId(), null para Google Calendar ou sem processo)
    String titulo,         // Título do evento (tarefa.titulo, "Parcela #N - R$X", evento.summary, audiencia.titulo)
    String clienteNome,    // Nome do cliente (null se não aplicável)
    String processoNumero, // Nº do processo (null se não aplicável)
    BigDecimal valor,      // Valor (parcela.valor, null para outros)
    String prioridade,     // Prioridade da tarefa (null para outros)
    LocalDate data,        // Data do evento (prazo, vencimento, data da audiência ou do evento Google)
    String horaInicio,     // Horário início (HH:mm, preenchido para AUDIENCIA, tarefas com hora e Google Calendar, null para dia inteiro)
    String horaFim,        // Horário fim (HH:mm, só para Google Calendar, null para outros)
    String htmlLink,       // Link externo (Google Calendar htmlLink, null para outros)
    String cor             // Cor do indicador visual: "orange", "green", "blue", "#e53935"
) {}
