package com.jurisflow.modules.importacao.dto;

import java.util.List;

public record ImportResultResponse(
    int colunasCriadas,
    int processosCriados,
    List<String> erros
) {}
