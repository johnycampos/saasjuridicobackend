package com.jurisflow.modules.movimento;

import com.jurisflow.modules.movimento.dto.MovimentoSyncResult;
import com.jurisflow.modules.movimento.dto.ProcessoMovimentosPayload;
import com.jurisflow.modules.movimento.dto.ProcessoPublicacoesPayload;
import com.jurisflow.modules.movimento.dto.PublicacaoSyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orquestra a sincronizacao de um lote com varios processos (potencialmente
 * de tenants diferentes). Deliberadamente NAO transacional aqui: cada item
 * roda em sua propria transacao dentro de MovimentoService.syncProcesso
 * (bean diferente, entao a chamada abaixo passa pelo proxy do Spring e o
 * @Transactional daquele metodo eh respeitado — se isso fosse uma chamada
 * interna dentro da mesma classe, o AOP proxy nao interceptaria e a
 * anotacao seria ignorada).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovimentoSyncService {

    private final MovimentoService movimentoService;

    public List<MovimentoSyncResult> sync(List<ProcessoMovimentosPayload> processos) {
        return processos.stream()
                .map(movimentoService::syncProcesso)
                .toList();
    }

    public List<PublicacaoSyncResult> syncPublicacoes(List<ProcessoPublicacoesPayload> publicacoes) {
        return publicacoes.stream()
                .map(movimentoService::syncPublicacoesProcesso)
                .toList();
    }
}
