package com.jurisflow.modules.movimento.dto;

import java.util.UUID;

public record PublicacaoSyncResult(
    UUID tenantId,
    String numeroProcesso,
    boolean sucesso,
    Integer novasPublicacoes,
    String erro
) {
    public static PublicacaoSyncResult sucesso(UUID tenantId, String numeroProcesso, int novasPublicacoes) {
        return new PublicacaoSyncResult(tenantId, numeroProcesso, true, novasPublicacoes, null);
    }

    public static PublicacaoSyncResult erro(UUID tenantId, String numeroProcesso, String mensagem) {
        return new PublicacaoSyncResult(tenantId, numeroProcesso, false, null, mensagem);
    }
}
