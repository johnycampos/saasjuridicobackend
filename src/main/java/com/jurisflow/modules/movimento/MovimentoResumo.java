package com.jurisflow.modules.movimento;

import java.time.LocalDateTime;

public record MovimentoResumo(LocalDateTime ultimaMovimentacao, boolean naoLida) {
    public static final MovimentoResumo VAZIO = new MovimentoResumo(null, false);
}
