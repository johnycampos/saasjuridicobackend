package com.jurisflow.modules.movimento;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jurisflow.modules.movimento.dto.MovimentoSyncItem;
import com.jurisflow.modules.movimento.dto.MovimentoSyncResult;
import com.jurisflow.modules.movimento.dto.ProcessoMovimentosPayload;
import com.jurisflow.modules.movimento.dto.ProcessoPublicacoesPayload;
import com.jurisflow.modules.movimento.dto.PublicacaoSyncItem;
import com.jurisflow.modules.movimento.dto.PublicacaoSyncResult;
import com.jurisflow.modules.processo.Processo;
import com.jurisflow.modules.processo.ProcessoRepository;
import com.jurisflow.modules.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovimentoServiceTest {

    @Mock
    private MovimentoRepository movimentoRepository;

    @Mock
    private ProcessoRepository processoRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MovimentoService movimentoService;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(tenantRepository.existsById(tenantId)).thenReturn(true);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    private ProcessoMovimentosPayload payload(String numeroProcesso) {
        return new ProcessoMovimentosPayload(tenantId, numeroProcesso, List.of(
                new MovimentoSyncItem(11010, "Mero expediente", "2026-05-19T05:11:25.000Z"),
                new MovimentoSyncItem(12164, "Outras Decisões", "2026-03-11T15:43:14.000Z")
        ));
    }

    private ProcessoPublicacoesPayload publicacaoPayload(String numeroProcesso) {
        return new ProcessoPublicacoesPayload(tenantId, numeroProcesso, List.of(
                new PublicacaoSyncItem("709650109", "wEp4n8JxY12fJlGheTaqbvmR1ObAW3",
                        LocalDate.of(2026, 8, 27), "TJRJ", "Intimação", "Despacho",
                        "3ª Vara de Família", "Guarda de Família", "14671", 31857L, "D",
                        "https://www3.tjrj.jus.br/...", "Poder Judiciario ...", List.of())
        ));
    }

    @Test
    void syncProcesso_shouldReturnErro_whenTenantNaoExiste() {
        when(tenantRepository.existsById(tenantId)).thenReturn(false);

        MovimentoSyncResult result = movimentoService.syncProcesso(payload("5006378-15.2024.4.02.5120"));

        assertThat(result.sucesso()).isFalse();
        assertThat(result.erro()).isEqualTo("Tenant nao encontrado");
    }

    @Test
    void syncProcesso_shouldReturnErro_whenProcessoNaoEncontrado() {
        when(processoRepository.findByTenantIdAndNumeroProcessoDigits(eq(tenantId), anyString()))
                .thenReturn(List.of());

        MovimentoSyncResult result = movimentoService.syncProcesso(payload("5006378-15.2024.4.02.5120"));

        assertThat(result.sucesso()).isFalse();
        assertThat(result.erro()).isEqualTo("Processo nao encontrado");
    }

    @Test
    void syncProcesso_shouldReturnErro_whenNumeroAmbiguo() {
        when(processoRepository.findByTenantIdAndNumeroProcessoDigits(eq(tenantId), anyString()))
                .thenReturn(List.of(new Processo(), new Processo()));

        MovimentoSyncResult result = movimentoService.syncProcesso(payload("5006378-15.2024.4.02.5120"));

        assertThat(result.sucesso()).isFalse();
        assertThat(result.erro()).contains("ambiguo");
    }

    @Test
    void syncProcesso_shouldNormalizarNumeroProcesso_antesDeBuscar() {
        when(processoRepository.findByTenantIdAndNumeroProcessoDigits(eq(tenantId), anyString()))
                .thenReturn(List.of());

        movimentoService.syncProcesso(payload("5006378-15.2024.4.02.5120"));

        // busca sempre so-digitos, independente de como o numero chegou no payload
        org.mockito.Mockito.verify(processoRepository)
                .findByTenantIdAndNumeroProcessoDigits(tenantId, "50063781520244025120");
    }

    @SuppressWarnings("unchecked")
    @Test
    void syncProcesso_shouldContarApenasLinhasRealmenteInseridas() {
        UUID processoId = UUID.randomUUID();
        Processo processo = new Processo();
        processo.setId(processoId);
        processo.setTenantId(tenantId);
        when(processoRepository.findByTenantIdAndNumeroProcessoDigits(eq(tenantId), anyString()))
                .thenReturn(List.of(processo));

        // ON CONFLICT DO NOTHING + RETURNING id: so volta o id de quem foi
        // realmente inserido — payload manda 2 itens, mas so 1 "novo" volta
        // (o outro caiu em conflito e nao aparece no RETURNING)
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(UUID.randomUUID()));

        MovimentoSyncResult result = movimentoService.syncProcesso(payload("5006378-15.2024.4.02.5120"));

        assertThat(result.sucesso()).isTrue();
        assertThat(result.novosMovimentos()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void syncProcesso_shouldReturnErro_semDerrubarChamada_quandoInsertFalha() {
        UUID processoId = UUID.randomUUID();
        Processo processo = new Processo();
        processo.setId(processoId);
        processo.setTenantId(tenantId);
        when(processoRepository.findByTenantIdAndNumeroProcessoDigits(eq(tenantId), anyString()))
                .thenReturn(List.of(processo));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenThrow(new RuntimeException("conexao caiu"));

        MovimentoSyncResult result = movimentoService.syncProcesso(payload("5006378-15.2024.4.02.5120"));

        assertThat(result.sucesso()).isFalse();
        assertThat(result.erro()).contains("conexao caiu");
    }

    @Test
    void syncPublicacoesProcesso_shouldReturnErro_whenTenantNaoExiste() {
        when(tenantRepository.existsById(tenantId)).thenReturn(false);

        PublicacaoSyncResult result = movimentoService.syncPublicacoesProcesso(publicacaoPayload("5006378-15.2024.4.02.5120"));

        assertThat(result.sucesso()).isFalse();
        assertThat(result.erro()).isEqualTo("Tenant nao encontrado");
    }

    @Test
    void syncPublicacoesProcesso_shouldReturnErro_whenProcessoNaoEncontrado() {
        when(processoRepository.findByTenantIdAndNumeroProcessoDigits(eq(tenantId), anyString()))
                .thenReturn(List.of());

        PublicacaoSyncResult result = movimentoService.syncPublicacoesProcesso(publicacaoPayload("5006378-15.2024.4.02.5120"));

        assertThat(result.sucesso()).isFalse();
        assertThat(result.erro()).isEqualTo("Processo nao encontrado");
    }

    @SuppressWarnings("unchecked")
    @Test
    void syncPublicacoesProcesso_shouldComporNome_apartirDeTipoComunicacaoETipoDocumento() {
        UUID processoId = UUID.randomUUID();
        Processo processo = new Processo();
        processo.setId(processoId);
        processo.setTenantId(tenantId);
        when(processoRepository.findByTenantIdAndNumeroProcessoDigits(eq(tenantId), anyString()))
                .thenReturn(List.of(processo));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(UUID.randomUUID()));

        movimentoService.syncPublicacoesProcesso(publicacaoPayload("5006378-15.2024.4.02.5120"));

        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), paramsCaptor.capture());
        // ordem dos params no bulkInsertPublicacoes: tenantId, processoId, nome, dataHora, dadosExtras, hashOrigem
        Object[] params = paramsCaptor.getValue();
        assertThat(params[2]).isEqualTo("Intimação - Despacho");
        assertThat(params[5]).isEqualTo("709650109");
    }

    @SuppressWarnings("unchecked")
    @Test
    void syncPublicacoesProcesso_shouldSerIdempotente_viaHashOrigem() {
        UUID processoId = UUID.randomUUID();
        Processo processo = new Processo();
        processo.setId(processoId);
        processo.setTenantId(tenantId);
        when(processoRepository.findByTenantIdAndNumeroProcessoDigits(eq(tenantId), anyString()))
                .thenReturn(List.of(processo));

        // 2a sincronizacao do mesmo externalId: ON CONFLICT DO NOTHING + RETURNING id
        // nao devolve nenhuma linha (ja existia) — novasPublicacoes precisa ser 0
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        PublicacaoSyncResult result = movimentoService.syncPublicacoesProcesso(publicacaoPayload("5006378-15.2024.4.02.5120"));

        assertThat(result.sucesso()).isTrue();
        assertThat(result.novasPublicacoes()).isEqualTo(0);
    }

    @SuppressWarnings("unchecked")
    @Test
    void syncPublicacoesProcesso_shouldReturnErro_semDerrubarChamada_quandoInsertFalha() {
        UUID processoId = UUID.randomUUID();
        Processo processo = new Processo();
        processo.setId(processoId);
        processo.setTenantId(tenantId);
        when(processoRepository.findByTenantIdAndNumeroProcessoDigits(eq(tenantId), anyString()))
                .thenReturn(List.of(processo));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenThrow(new RuntimeException("conexao caiu"));

        PublicacaoSyncResult result = movimentoService.syncPublicacoesProcesso(publicacaoPayload("5006378-15.2024.4.02.5120"));

        assertThat(result.sucesso()).isFalse();
        assertThat(result.erro()).contains("conexao caiu");
    }
}
