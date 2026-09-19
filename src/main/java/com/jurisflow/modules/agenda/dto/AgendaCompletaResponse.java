package com.jurisflow.modules.agenda.dto;

import java.util.List;

public record AgendaCompletaResponse(
    List<AgendaEventoResponse> eventos,
    AgendaResumoResponse resumo
) {}
