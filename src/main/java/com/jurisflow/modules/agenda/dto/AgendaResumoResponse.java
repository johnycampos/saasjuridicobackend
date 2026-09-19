package com.jurisflow.modules.agenda.dto;

public record AgendaResumoResponse(
    int totalTarefas,
    int totalParcelas,
    int totalAudiencias,
    int totalEventosGoogle,
    int totalAtrasados,    // tarefas com prazo < hoje
    int totalUrgentes      // tarefas URGENTE não concluídas
) {}
