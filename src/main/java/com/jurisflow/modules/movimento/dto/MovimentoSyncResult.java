package com.jurisflow.modules.movimento.dto;

import java.util.UUID;

public record MovimentoSyncResult(
    UUID tenantId,
    String numeroProcesso,
    boolean sucesso,
    Integer novosMovimentos,
    String erro
) {
    public static MovimentoSyncResult sucesso(UUID tenantId, String numeroProcesso, int novosMovimentos) {
        return new MovimentoSyncResult(tenantId, numeroProcesso, true, novosMovimentos, null);
    }

    public static MovimentoSyncResult erro(UUID tenantId, String numeroProcesso, String mensagem) {
        return new MovimentoSyncResult(tenantId, numeroProcesso, false, null, mensagem);
    }
}
