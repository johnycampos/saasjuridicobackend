package com.jurisflow.modules.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jurisflow.modules.audiencia.Audiencia;
import com.jurisflow.modules.audiencia.AudienciaRepository;
import com.jurisflow.modules.audiencia.AudienciaService;
import com.jurisflow.modules.financeiro.*;
import com.jurisflow.modules.financeiro.dto.ContratoResponse;
import com.jurisflow.modules.financeiro.dto.ParcelaResponse;
import com.jurisflow.modules.group.GroupService;
import com.jurisflow.modules.mcp.server.McpController;
import com.jurisflow.modules.mcp.server.tools.*;
import com.jurisflow.modules.processo.Processo;
import com.jurisflow.modules.processo.ProcessoRepository;
import com.jurisflow.modules.processo.ProcessoService;
import com.jurisflow.modules.tarefa.Tarefa;
import com.jurisflow.modules.tarefa.TarefaRepository;
import com.jurisflow.modules.tarefa.TarefaService;
import com.jurisflow.modules.tenant.TenantMember;
import com.jurisflow.modules.tenant.TenantMemberRepository;
import com.jurisflow.modules.tenant.TenantRole;
import com.jurisflow.security.TenantAccessGuard;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class McpParteAToolsTest {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final UUID tenant = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final UUID areaA = UUID.randomUUID();
    final UUID areaB = UUID.randomUUID();
    final UUID processIdA = UUID.randomUUID();
    final UUID processIdB = UUID.randomUUID();

    final UserPrincipal memberUser = new UserPrincipal(userId, "member@test", "Member", null);
    final UserPrincipal viewerUser = new UserPrincipal(UUID.randomUUID(), "viewer@test", "Viewer", null);

    final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    final GroupService groupService = mock(GroupService.class);
    final ProcessoService processoService = mock(ProcessoService.class);
    final TarefaRepository tarefaRepository = mock(TarefaRepository.class);
    final TarefaService tarefaService = mock(TarefaService.class);
    final AudienciaRepository audienciaRepository = mock(AudienciaRepository.class);
    final AudienciaService audienciaService = mock(AudienciaService.class);
    final ContratoRepository contratoRepository = mock(ContratoRepository.class);
    final ParcelaRepository parcelaRepository = mock(ParcelaRepository.class);
    final ContratoService contratoService = mock(ContratoService.class);
    final com.jurisflow.modules.movimento.MovimentoService movimentos = mock(com.jurisflow.modules.movimento.MovimentoService.class);
    final TenantMemberRepository members = mock(TenantMemberRepository.class);
    final TenantAccessGuard guard = new TenantAccessGuard(members);

    McpWriteService writeService;
    McpController controller;

    @BeforeEach
    void setup() {
        TenantContext.setCurrentTenantId(tenant);

        TenantMember member = new TenantMember();
        member.setRole(TenantRole.MEMBER);
        member.setAtivo(true);
        when(members.findActiveMember(tenant, userId)).thenReturn(Optional.of(member));
        when(members.existsByTenantIdAndUser_IdAndAtivoTrue(tenant, userId)).thenReturn(true);

        TenantMember viewer = new TenantMember();
        viewer.setRole(TenantRole.VIEWER);
        viewer.setAtivo(true);
        when(members.findActiveMember(tenant, viewerUser.getId())).thenReturn(Optional.of(viewer));
        when(members.existsByTenantIdAndUser_IdAndAtivoTrue(tenant, viewerUser.getId())).thenReturn(true);

        // member can only access areaA
        when(groupService.resolveGroupRestriction(tenant, userId)).thenReturn(Optional.of(Set.of(areaA)));
        when(groupService.resolveGroupRestriction(tenant, viewerUser.getId())).thenReturn(Optional.of(Set.of(areaA)));

        Processo pA = new Processo();
        pA.setId(processIdA);
        pA.setTenantId(tenant);
        pA.setGroupId(areaA);

        Processo pB = new Processo();
        pB.setId(processIdB);
        pB.setTenantId(tenant);
        pB.setGroupId(areaB);

        when(processoRepository.findByIdAndTenantIdForUpdate(processIdA, tenant)).thenReturn(Optional.of(pA));
        when(processoRepository.findByIdAndTenantIdForUpdate(processIdB, tenant)).thenReturn(Optional.of(pB));

        writeService = new McpWriteService(processoRepository, groupService, processoService,
                tarefaRepository, tarefaService, audienciaRepository,
                contratoRepository, parcelaRepository, contratoService, movimentos);

        var arquivarTool = new ArquivarProcessoTool(writeService);
        var excluirTarefaTool = new ExcluirTarefaTool(writeService);
        var cancelarAudienciaTool = new CancelarAudienciaTool(writeService);
        var excluirParcelaTool = new ExcluirParcelaTool(writeService);
        var financeiroProcTool = new ConsultarFinanceiroProcessoTool(contratoService);
        var financeiroCliTool = new ListarFinanceiroClienteTool(contratoService);
        var definirContratoTool = new DefinirContratoTool(writeService);
        var adicionarParcelaTool = new AdicionarParcelaTool(writeService);
        var marcarParcelaPagaTool = new MarcarParcelaPagaTool(writeService);

        controller = new McpController(mapper, List.of(
                arquivarTool, excluirTarefaTool, cancelarAudienciaTool, excluirParcelaTool,
                financeiroProcTool, financeiroCliTool, definirContratoTool, adicionarParcelaTool, marcarParcelaPagaTool,
                new AdicionarMovimentacaoTool(writeService)
        ), guard);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private JsonNode call(String toolName, Map<String, Object> args, UserPrincipal principal) {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\""
                + toolName + "\",\"arguments\":" + toJson(args) + "}}";
        var res = controller.handle(req, principal);
        return mapper.valueToTree(res.getBody()).path("result");
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void movimentacaoManualRespeitaAreaTenantViewerEData() {
        var args = new HashMap<String, Object>(Map.of("processoId", processIdA,
            "nome", "Decisão recebida por e-mail", "dataHora", "2026-10-11T14:30:00"));
        var response = new com.jurisflow.modules.movimento.dto.MovimentoResponse(UUID.randomUUID(), tenant,
            processIdA, null, "Decisão", LocalDateTime.of(2026, 10, 11, 14, 30), "{}", false,
            null, null, LocalDateTime.now(), com.jurisflow.modules.movimento.MovimentoOrigem.MOVIMENTO);
        when(movimentos.adicionarManual(any(), any(), any(), any(), any())).thenReturn(response);
        var ok = call("adicionar_movimentacao", args, memberUser);
        assertThat(ok.path("isError").asBoolean()).isFalse();
        assertThat(ok.path("structuredContent").has("tenantId")).isFalse();
        verify(movimentos).adicionarManual(processIdA, "Decisão recebida por e-mail",
            LocalDateTime.of(2026, 10, 11, 14, 30), null, memberUser);
        clearInvocations(movimentos);
        assertThat(call("adicionar_movimentacao", args, viewerUser).path("isError").asBoolean()).isTrue();
        args.put("processoId", processIdB);
        assertThat(call("adicionar_movimentacao", args, memberUser).path("isError").asBoolean()).isTrue();
        args.put("processoId", UUID.randomUUID());
        assertThat(call("adicionar_movimentacao", args, memberUser).path("isError").asBoolean()).isTrue();
        args.put("processoId", processIdA);
        for (String invalid : List.of("2026-02-30T14:30:00", "2026-10-11T14:30:00Z", "ontem")) {
            args.put("dataHora", invalid);
            assertThat(call("adicionar_movimentacao", args, memberUser).path("isError").asBoolean()).isTrue();
        }
        args.put("dataHora", "2026-10-11T14:30:00");
        for (String invalid : List.of(" ", "x".repeat(501))) {
            args.put("nome", invalid);
            assertThat(call("adicionar_movimentacao", args, memberUser).path("isError").asBoolean()).isTrue();
        }
        verifyNoInteractions(movimentos);
    }

    @Test
    void toolsListContainsAnnotationsWithHintsAndTitles() {
        var res = controller.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}", memberUser);
        JsonNode root = mapper.valueToTree(res.getBody());
        JsonNode tools = root.path("result").path("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools.size()).isEqualTo(10);

        for (JsonNode t : tools) {
            assertThat(t.hasNonNull("annotations")).isTrue();
            JsonNode ann = t.get("annotations");
            assertThat(ann.hasNonNull("title")).isTrue();
            assertThat(ann.hasNonNull("readOnlyHint")).isTrue();
            if (t.get("name").asText().startsWith("arquivar_") || t.get("name").asText().startsWith("excluir_")
                    || t.get("name").asText().startsWith("cancelar_")) {
                assertThat(ann.get("destructiveHint").asBoolean()).isTrue();
                assertThat(ann.get("readOnlyHint").asBoolean()).isFalse();
            }
        }
    }

    @Test
    void arquivarProcessoExigeConfirmarTrue() {
        var resNoConfirm = call("arquivar_processo", Map.of("processoId", processIdA), memberUser);
        assertThat(resNoConfirm.path("isError").asBoolean()).isTrue();

        var resFalse = call("arquivar_processo", Map.of("processoId", processIdA, "confirmar", false), memberUser);
        assertThat(resFalse.path("isError").asBoolean()).isTrue();
        assertThat(resFalse.path("content").get(0).path("text").asText()).contains("confirmar=true");
        verify(processoService, never()).archive(any(), any());
    }

    @Test
    void arquivarProcessoSucessoEmAreaAutorizada() {
        var res = call("arquivar_processo", Map.of("processoId", processIdA, "confirmar", true), memberUser);
        assertThat(res.path("isError").asBoolean()).isFalse();
        verify(processoService).archive(processIdA, memberUser);
    }

    @Test
    void arquivarProcessoNegadoEmAreaNaoAutorizadaOuViewer() {
        var resAreaB = call("arquivar_processo", Map.of("processoId", processIdB, "confirmar", true), memberUser);
        assertThat(resAreaB.path("isError").asBoolean()).isTrue();
        assertThat(resAreaB.path("content").get(0).path("text").asText()).contains("Acesso negado");

        var resViewer = call("arquivar_processo", Map.of("processoId", processIdA, "confirmar", true), viewerUser);
        assertThat(resViewer.path("isError").asBoolean()).isTrue();
        assertThat(resViewer.path("content").get(0).path("text").asText()).contains("apenas leitura");
    }

    @Test
    void excluirTarefaExigeConfirmarEAreaAutorizada() {
        UUID tarefaIdA = UUID.randomUUID();
        Tarefa tA = new Tarefa();
        tA.setId(tarefaIdA);
        tA.setTenantId(tenant);
        tA.setProcessoId(processIdA);
        when(tarefaRepository.findByIdAndTenantId(tarefaIdA, tenant)).thenReturn(Optional.of(tA));

        var resSemConfirm = call("excluir_tarefa", Map.of("tarefaId", tarefaIdA, "confirmar", false), memberUser);
        assertThat(resSemConfirm.path("isError").asBoolean()).isTrue();

        var resOk = call("excluir_tarefa", Map.of("tarefaId", tarefaIdA, "confirmar", true), memberUser);
        assertThat(resOk.path("isError").asBoolean()).isFalse();
        verify(tarefaService).delete(tarefaIdA);

        UUID tarefaIdB = UUID.randomUUID();
        Tarefa tB = new Tarefa();
        tB.setId(tarefaIdB);
        tB.setTenantId(tenant);
        tB.setProcessoId(processIdB);
        when(tarefaRepository.findByIdAndTenantId(tarefaIdB, tenant)).thenReturn(Optional.of(tB));

        var resAreaB = call("excluir_tarefa", Map.of("tarefaId", tarefaIdB, "confirmar", true), memberUser);
        assertThat(resAreaB.path("isError").asBoolean()).isTrue();
    }

    @Test
    void cancelarAudienciaExigeConfirmarEAreaAutorizada() {
        UUID audId = UUID.randomUUID();
        Audiencia aud = new Audiencia();
        aud.setId(audId);
        aud.setTenantId(tenant);
        aud.setProcessoId(processIdA);
        when(audienciaRepository.findByIdAndTenantIdForUpdate(audId, tenant)).thenReturn(Optional.of(aud));

        var resSemConfirm = call("cancelar_audiencia", Map.of("audienciaId", audId, "confirmar", false), memberUser);
        assertThat(resSemConfirm.path("isError").asBoolean()).isTrue();

        var resOk = call("cancelar_audiencia", Map.of("audienciaId", audId, "confirmar", true), memberUser);
        assertThat(resOk.path("isError").asBoolean()).isFalse();
        assertThat(aud.getStatus()).isEqualTo("CANCELADA");
        verify(audienciaRepository).save(aud);
    }

    @Test
    void excluirParcelaPermiteApenasPendente() {
        UUID parcelaId = UUID.randomUUID();
        UUID contratoId = UUID.randomUUID();
        Parcela p = new Parcela(); p.setId(parcelaId); p.setTenantId(tenant); p.setContratoId(contratoId);
        Contrato c = new Contrato(); c.setId(contratoId); c.setTenantId(tenant); c.setProcessoId(processIdA);
        when(parcelaRepository.findByIdAndTenantId(parcelaId, tenant)).thenReturn(Optional.of(p));
        when(contratoRepository.findByIdAndTenantId(contratoId, tenant)).thenReturn(Optional.of(c));

        doThrow(new BusinessException("Parcela ja paga ou inexistente"))
                .when(contratoService).deleteParcela(parcelaId, memberUser);

        var resPaga = call("excluir_parcela", Map.of("parcelaId", parcelaId, "confirmar", true), memberUser);
        assertThat(resPaga.path("isError").asBoolean()).isTrue();
        assertThat(resPaga.path("content").get(0).path("text").asText()).contains("Parcela ja paga ou inexistente");

        UUID parcelaPendId = UUID.randomUUID();
        Parcela pPend = new Parcela(); pPend.setId(parcelaPendId); pPend.setTenantId(tenant); pPend.setContratoId(contratoId);
        when(parcelaRepository.findByIdAndTenantId(parcelaPendId, tenant)).thenReturn(Optional.of(pPend));

        var resOk = call("excluir_parcela", Map.of("parcelaId", parcelaPendId, "confirmar", true), memberUser);
        assertThat(resOk.path("isError").asBoolean()).isFalse();
        verify(contratoService).deleteParcela(parcelaPendId, memberUser);
    }

    @Test
    void consultarFinanceiroProcessoRetornaNullSemErroQuandoNaoExisteContrato() {
        when(contratoService.getByProcesso(processIdA, userId))
                .thenThrow(BusinessException.notFound("Contrato"));

        var res = call("consultar_financeiro_processo", Map.of("processoId", processIdA), memberUser);
        assertThat(res.path("isError").asBoolean()).isFalse();
        assertThat(res.path("structuredContent").get("contrato").isNull()).isTrue();
        assertThat(res.path("structuredContent").get("parcelas").isArray()).isTrue();
    }

    @Test
    void consultarFinanceiroProcessoRetornaErroQuandoSemAcessoAoProcesso() {
        when(contratoService.getByProcesso(processIdB, userId))
                .thenThrow(BusinessException.forbidden());

        var res = call("consultar_financeiro_processo", Map.of("processoId", processIdB), memberUser);
        assertThat(res.path("isError").asBoolean()).isTrue();
        assertThat(res.path("content").get(0).path("text").asText()).contains("Acesso negado");
    }

    @Test
    void definirContratoValidaValorPositivoEMaxDuasCasas() {
        // negativo
        var resNeg = call("definir_contrato", Map.of("processoId", processIdA, "valorTotal", -50.0), memberUser);
        assertThat(resNeg.path("isError").asBoolean()).isTrue();

        // 3 casas decimais
        var resDec = call("definir_contrato", Map.of("processoId", processIdA, "valorTotal", 100.555), memberUser);
        assertThat(resDec.path("isError").asBoolean()).isTrue();

        // válido
        when(contratoService.createOrUpdate(any(), any(), any())).thenReturn(new ContratoResponse(
                UUID.randomUUID(), tenant, processIdA, BigDecimal.valueOf(1500.50), BigDecimal.valueOf(1500.50),
                "obs", userId, LocalDateTime.now(), LocalDateTime.now(), List.of()
        ));
        var resOk = call("definir_contrato", Map.of("processoId", processIdA, "valorTotal", 1500.50), memberUser);
        assertThat(resOk.path("isError").asBoolean()).isFalse();
    }

    @Test
    void adicionarParcelaExigeContratoPrevio() {
        when(contratoRepository.findByProcessoIdAndTenantId(processIdA, tenant))
                .thenReturn(Optional.empty());

        var res = call("adicionar_parcela", Map.of("processoId", processIdA, "valor", 250.0), memberUser);
        assertThat(res.path("isError").asBoolean()).isTrue();
        assertThat(res.path("content").get(0).path("text").asText()).contains("Defina o contrato antes");
    }
}
