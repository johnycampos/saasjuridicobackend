package com.jurisflow.modules.tarefa;

import com.jurisflow.modules.processo.PrioridadeTipo;

import java.time.LocalDate;

public record TarefaResumo(LocalDate prazo, PrioridadeTipo prioridade) {
    public static final TarefaResumo VAZIO = new TarefaResumo(null, null);
}
