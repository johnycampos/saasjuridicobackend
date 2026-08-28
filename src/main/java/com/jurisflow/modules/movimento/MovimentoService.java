package com.jurisflow.modules.movimento;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jurisflow.modules.movimento.dto.MovimentoResponse;
import com.jurisflow.modules.movimento.dto.MovimentoSyncItem;
import com.jurisflow.modules.movimento.dto.MovimentoSyncResult;
import com.jurisflow.modules.movimento.dto.ProcessoMovimentosPayload;
import com.jurisflow.modules.movimento.dto.ProcessoPublicacoesPayload;
import com.jurisflow.modules.movimento.dto.PublicacaoSyncItem;
import com.jurisflow.modules.movimento.dto.PublicacaoSyncResult;
import com.jurisflow.modules.processo.Processo;
import com.jurisflow.modules.processo.ProcessoRepository;
import com.jurisflow.modules.tenant.TenantRepository;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovimentoService {

    private final MovimentoRepository movimentoRepository;
    private final ProcessoRepository processoRepository;
    private final TenantRepository tenantRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public List<MovimentoResponse> listByProcesso(UUID processoId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        validateProcessoInTenant(processoId, tenantId);
        return movimentoRepository.findByProcessoIdOrderByDataHoraDesc(processoId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public MovimentoResponse toggleVisualizado(UUID id, boolean visualizado, UserPrincipal principal) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Movimento movimento = movimentoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Movimentacao"));

        movimento.setVisualizado(visualizado);
        movimento.setVisualizadoEm(visualizado ? LocalDateTime.now() : null);
        movimento.setVisualizadoPor(visualizado ? principal.getId() : null);

        return toResponse(movimentoRepository.save(movimento));
    }

    public Map<UUID, MovimentoResumo> resumoPorProcesso(Collection<UUID> processoIds) {
        if (processoIds.isEmpty()) return Map.of();

        return movimentoRepository.findByProcessoIdIn(processoIds).stream()
                .collect(Collectors.groupingBy(Movimento::getProcessoId))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    var movs = e.getValue();
                    var ultima = movs.stream().map(Movimento::getDataHora).filter(Objects::nonNull)
                            .max(Comparator.naturalOrder()).orElse(null);
                    var naoLida = movs.stream().anyMatch(m -> !Boolean.TRUE.equals(m.getVisualizado()));
                    return new MovimentoResumo(ultima, naoLida);
                }));
    }

    /**
     * Sincroniza em lote os movimentos de UM processo, chamado pelo endpoint
     * de integracao (com.jurisflow.modules.movimento.MovimentoSyncController).
     * Metodo publico transacional numa unica chamada (nao um loop de save()
     * individual) para que o MovimentoSyncService, ao chamar isso repetidas
     * vezes para varios processos, tenha uma transacao isolada por item —
     * um processo com erro nao derruba os outros do mesmo lote.
     *
     * Idempotente: reenviar o mesmo payload nao duplica linhas nem altera
     * visualizado/visualizado_em/visualizado_por de movimentos ja existentes
     * (ON CONFLICT (processo_id, codigo, data_hora) DO NOTHING).
     */
    @Transactional
    public MovimentoSyncResult syncProcesso(ProcessoMovimentosPayload payload) {
        UUID tenantId = payload.tenantId();
        String numeroOriginal = payload.numeroProcesso();

        if (!tenantRepository.existsById(tenantId)) {
            return MovimentoSyncResult.erro(tenantId, numeroOriginal, "Tenant nao encontrado");
        }

        String numeroDigits = numeroOriginal.replaceAll("\\D", "");
        if (numeroDigits.isBlank()) {
            return MovimentoSyncResult.erro(tenantId, numeroOriginal, "numeroProcesso sem digitos validos");
        }

        List<Processo> encontrados = processoRepository.findByTenantIdAndNumeroProcessoDigits(tenantId, numeroDigits);
        if (encontrados.isEmpty()) {
            return MovimentoSyncResult.erro(tenantId, numeroOriginal, "Processo nao encontrado");
        }
        if (encontrados.size() > 1) {
            return MovimentoSyncResult.erro(tenantId, numeroOriginal,
                    "Numero de processo ambiguo (" + encontrados.size() + " processos encontrados)");
        }

        try {
            int novos = bulkInsert(tenantId, encontrados.get(0).getId(), payload.movimentos());
            return MovimentoSyncResult.sucesso(tenantId, numeroOriginal, novos);
        } catch (Exception e) {
            log.error("Falha ao sincronizar movimentos do processo {} (tenant {}): {}",
                    numeroOriginal, tenantId, e.getMessage(), e);
            return MovimentoSyncResult.erro(tenantId, numeroOriginal, "Erro ao gravar movimentos: " + e.getMessage());
        }
    }

    /**
     * Uma unica instrucao INSERT multi-VALUES (nao jdbcTemplate.batchUpdate):
     * com reWriteBatchedInserts=true (padrao do driver pgjdbc moderno), um
     * batch de INSERTs e reescrito pelo driver num unico INSERT multi-linha
     * antes de ir pro servidor — e nesse caminho o Postgres devolve so um
     * command tag ("INSERT 0 N") pro lote inteiro, entao o driver nao
     * consegue reportar um update count por linha (todas voltam como
     * Statement.SUCCESS_NO_INFO), o que quebraria a contagem de "quantas
     * linhas novas entraram de fato". RETURNING id contorna isso: só volta
     * o id de quem foi REALMENTE inserido (uma linha em conflito, por causa
     * do ON CONFLICT DO NOTHING, simplesmente nao aparece no retorno) — o
     * tamanho da lista resultante já é a contagem correta.
     */
    private int bulkInsert(UUID tenantId, UUID processoId, List<MovimentoSyncItem> itens) {
        if (itens.isEmpty()) return 0;

        StringBuilder sql = new StringBuilder(
                "INSERT INTO processo_movimentos (id, tenant_id, processo_id, codigo, nome, data_hora, dados_extras, visualizado, created_at, updated_at) VALUES ");
        List<Object> params = new ArrayList<>(itens.size() * 5);
        for (int i = 0; i < itens.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("(gen_random_uuid(), ?, ?, ?, ?, ?, '{}', false, now(), now())");
            MovimentoSyncItem item = itens.get(i);
            params.add(tenantId);
            params.add(processoId);
            params.add(item.codigo());
            params.add(item.nome());
            params.add(parseDataHora(item.dataHora()));
        }
        sql.append(" ON CONFLICT (processo_id, codigo, data_hora) DO NOTHING RETURNING id");

        List<UUID> inseridos = jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> (UUID) rs.getObject("id"), params.toArray());
        return inseridos.size();
    }

    /**
     * Sincroniza em lote as publicacoes (Comunica/DJEN) de UM processo —
     * mesmo esqueleto de syncProcesso (tenant -> processo por numero ->
     * insert idempotente -> try/catch por item), so muda a chave de dedupe
     * (hash_origem = externalId, via indice parcial uq_proc_mov_hash_origem)
     * e o mapeamento dos campos.
     */
    @Transactional
    public PublicacaoSyncResult syncPublicacoesProcesso(ProcessoPublicacoesPayload payload) {
        UUID tenantId = payload.tenantId();
        String numeroOriginal = payload.numeroProcesso();

        if (!tenantRepository.existsById(tenantId)) {
            return PublicacaoSyncResult.erro(tenantId, numeroOriginal, "Tenant nao encontrado");
        }

        String numeroDigits = numeroOriginal.replaceAll("\\D", "");
        if (numeroDigits.isBlank()) {
            return PublicacaoSyncResult.erro(tenantId, numeroOriginal, "numeroProcesso sem digitos validos");
        }

        List<Processo> encontrados = processoRepository.findByTenantIdAndNumeroProcessoDigits(tenantId, numeroDigits);
        if (encontrados.isEmpty()) {
            return PublicacaoSyncResult.erro(tenantId, numeroOriginal, "Processo nao encontrado");
        }
        if (encontrados.size() > 1) {
            return PublicacaoSyncResult.erro(tenantId, numeroOriginal,
                    "Numero de processo ambiguo (" + encontrados.size() + " processos encontrados)");
        }

        try {
            int novas = bulkInsertPublicacoes(tenantId, encontrados.get(0).getId(), payload.itens());
            return PublicacaoSyncResult.sucesso(tenantId, numeroOriginal, novas);
        } catch (Exception e) {
            log.error("Falha ao sincronizar publicacoes do processo {} (tenant {}): {}",
                    numeroOriginal, tenantId, e.getMessage(), e);
            return PublicacaoSyncResult.erro(tenantId, numeroOriginal, "Erro ao gravar publicacoes: " + e.getMessage());
        }
    }

    // Mesma tecnica de bulkInsert: multi-VALUES + RETURNING id numa unica
    // instrucao (nao jdbcTemplate.batchUpdate — ver comentario em bulkInsert).
    // ON CONFLICT tem que repetir o WHERE do indice parcial pra Postgres
    // conseguir inferir qual indice usar (uq_proc_mov_hash_origem, so sobre
    // hash_origem, nao afeta a unique (processo_id, codigo, data_hora) dos
    // movimentos porque codigo fica NULL aqui).
    private int bulkInsertPublicacoes(UUID tenantId, UUID processoId, List<PublicacaoSyncItem> itens) {
        if (itens.isEmpty()) return 0;

        StringBuilder sql = new StringBuilder(
                "INSERT INTO processo_movimentos (id, tenant_id, processo_id, codigo, nome, data_hora, dados_extras, visualizado, origem, hash_origem, created_at, updated_at) VALUES ");
        List<Object> params = new ArrayList<>(itens.size() * 5);
        for (int i = 0; i < itens.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("(gen_random_uuid(), ?, ?, NULL, ?, ?, ?, false, 'PUBLICACAO', ?, now(), now())");
            PublicacaoSyncItem item = itens.get(i);
            params.add(tenantId);
            params.add(processoId);
            params.add(buildNomePublicacao(item));
            params.add(item.dataDisponibilizacao().atStartOfDay());
            params.add(buildDadosExtrasPublicacao(item));
            params.add(item.externalId());
        }
        sql.append(" ON CONFLICT (processo_id, hash_origem) WHERE hash_origem IS NOT NULL DO NOTHING RETURNING id");

        List<UUID> inseridos = jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> (UUID) rs.getObject("id"), params.toArray());
        return inseridos.size();
    }

    private String buildNomePublicacao(PublicacaoSyncItem item) {
        if (item.tipoDocumento() == null || item.tipoDocumento().isBlank()) {
            return item.tipoComunicacao();
        }
        return item.tipoComunicacao() + " - " + item.tipoDocumento();
    }

    private String buildDadosExtrasPublicacao(PublicacaoSyncItem item) {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("hash", item.hash());
        extras.put("siglaTribunal", item.siglaTribunal());
        extras.put("tipoComunicacao", item.tipoComunicacao());
        extras.put("tipoDocumento", item.tipoDocumento());
        extras.put("nomeOrgao", item.nomeOrgao());
        extras.put("nomeClasse", item.nomeClasse());
        extras.put("codigoClasse", item.codigoClasse());
        extras.put("numeroComunicacao", item.numeroComunicacao());
        extras.put("meio", item.meio());
        extras.put("link", item.link());
        extras.put("texto", item.texto());
        extras.put("advogados", item.advogados());
        try {
            return objectMapper.writeValueAsString(extras);
        } catch (Exception e) {
            log.warn("Falha ao serializar dados_extras da publicacao {}: {}", item.externalId(), e.getMessage());
            return "{}";
        }
    }

    private LocalDateTime parseDataHora(String raw) {
        try {
            return Instant.parse(raw).atZone(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception e) {
            throw new IllegalArgumentException("dataHora invalida (esperado ISO-8601, ex: 2026-05-19T05:11:25.000Z): " + raw, e);
        }
    }

    private void validateProcessoInTenant(UUID processoId, UUID tenantId) {
        processoRepository.findByIdAndTenantId(processoId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Processo"));
    }

    private MovimentoResponse toResponse(Movimento m) {
        return new MovimentoResponse(m.getId(), m.getTenantId(), m.getProcessoId(),
                m.getCodigo(), m.getNome(), m.getDataHora(), m.getDadosExtras(),
                m.getVisualizado(), m.getVisualizadoPor(), m.getVisualizadoEm(), m.getCreatedAt(),
                m.getOrigem());
    }
}
