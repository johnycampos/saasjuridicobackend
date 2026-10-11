package com.jurisflow.modules.mcp;

import com.fasterxml.jackson.databind.*;
import com.jurisflow.modules.mcp.server.*;
import com.jurisflow.modules.mcp.server.tools.*;
import com.jurisflow.modules.processo.*;
import com.jurisflow.modules.group.*;
import com.jurisflow.modules.board.*;
import com.jurisflow.modules.cliente.*;
import com.jurisflow.modules.tarefa.*;
import com.jurisflow.modules.movimento.*;
import com.jurisflow.modules.audiencia.*;
import com.jurisflow.modules.processolink.*;
import com.jurisflow.modules.tenant.*;
import com.jurisflow.security.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class McpToolsIsolationTest {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final UUID tenant = UUID.randomUUID(), userId = UUID.randomUUID(), areaA = UUID.randomUUID(), areaB = UUID.randomUUID(), processId = UUID.randomUUID();
    final UserPrincipal user = new UserPrincipal(userId, "member@test", "Member", null);
    final ProcessoRepository processRepository = mock(ProcessoRepository.class);
    final GroupService groups = mock(GroupService.class);
    final ClienteRepository clienteRepository = mock(ClienteRepository.class);
    final ClienteService clientes = mock(ClienteService.class);
    final BoardColumnRepository columns = mock(BoardColumnRepository.class);
    final TarefaService tarefas = mock(TarefaService.class);
    final TarefaRepository tarefaRepository = mock(TarefaRepository.class);
    final MovimentoService movimentos = mock(MovimentoService.class);
    final ProcessoLinkService links = mock(ProcessoLinkService.class);
    final AudienciaService audiencias = mock(AudienciaService.class);
    final TenantMemberRepository members = mock(TenantMemberRepository.class);
    final TenantAccessGuard guard = new TenantAccessGuard(members);
    final ProcessoService processos = new ProcessoService(processRepository, clienteRepository, tarefas, movimentos, groups, mock(GroupRepository.class), columns);
    final McpTarefaWriteService tarefaWrites = new McpTarefaWriteService(processRepository, groups, tarefaRepository, tarefas);
    McpController controller;

    @BeforeEach void setup() {
        TenantContext.setCurrentTenantId(tenant);
        TenantMember member = new TenantMember(); member.setRole(TenantRole.MEMBER); member.setAtivo(true);
        when(members.findActiveMember(tenant, userId)).thenReturn(Optional.of(member));
        when(members.existsByTenantIdAndUser_IdAndAtivoTrue(tenant, userId)).thenReturn(true);
        when(groups.resolveGroupRestriction(tenant, userId)).thenReturn(Optional.of(Set.of(areaA)));
        controller = new McpController(mapper, List.of(
            new ObterProcessoTool(processos, tarefas, links, movimentos, audiencias),
            new ListarTarefasProcessoTool(processos, tarefas),
            new ListarMovimentacoesTool(processos, movimentos),
            new CriarTarefaTool(tarefaWrites),
            new ConcluirTarefaTool(tarefaWrites),
            new CriarProcessoTool(processos, groups, columns, clientes, clienteRepository)), guard);
    }
    @AfterEach void cleanup() { TenantContext.clear(); }
    private void processInArea(UUID area) {
        Processo p = new Processo(); p.setId(processId); p.setTenantId(tenant); p.setGroupId(area);
        when(processRepository.findByIdAndTenantId(processId, tenant)).thenReturn(Optional.of(p));
        when(processRepository.findByIdAndTenantIdForUpdate(processId, tenant)).thenReturn(Optional.of(p));
    }
    private JsonNode call(String tool, Map<String, Object> args) throws Exception {
        String request = mapper.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/call", "params", Map.of("name", tool, "arguments", args)));
        return mapper.valueToTree(controller.handle(request, user).getBody()).path("result");
    }
    @Test void memberInAreaACannotReadOrWriteProcessInAreaB() throws Exception {
        processInArea(areaB);
        for (String name : List.of("obter_processo", "listar_tarefas_processo", "listar_movimentacoes", "criar_tarefa")) {
            var args = new HashMap<String, Object>(); args.put("processoId", processId.toString());
            if (name.equals("criar_tarefa")) args.put("titulo", "Prazo");
            JsonNode result = call(name, args);
            assertThat(result.path("isError").asBoolean()).as(name).isTrue();
            assertThat(result.path("content").get(0).path("text").asText()).isEqualTo("Acesso negado");
        }
        verifyNoInteractions(tarefas, movimentos, links);
    }
    @Test void processFromAnotherTenantIsNotFoundBeforeDependentServices() throws Exception {
        assertThat(call("obter_processo", Map.of("processoId", processId)).path("isError").asBoolean()).isTrue();
        verify(processRepository).findByIdAndTenantId(processId, tenant);
        verifyNoInteractions(tarefas, movimentos, links);
    }
    @Test void cannotCreateInUnauthorizedAreaEvenWithAuthorizedClient() throws Exception {
        assertThat(call("criar_processo", Map.of("areaId", areaB, "clienteNome", "Cliente")).path("isError").asBoolean()).isTrue();
        verifyNoInteractions(columns, clientes, clienteRepository);
        verify(processRepository, never()).save(any());
    }
    @Test void cannotCompleteTaskInAnotherArea() throws Exception {
        processInArea(areaB);
        UUID taskId = UUID.randomUUID(); Tarefa task = new Tarefa(); task.setProcessoId(processId);
        when(tarefaRepository.findByIdAndTenantId(taskId, tenant)).thenReturn(Optional.of(task));
        assertThat(call("concluir_tarefa", Map.of("tarefaId", taskId)).path("isError").asBoolean()).isTrue();
        verify(tarefas, never()).toggleConcluida(any(), anyBoolean(), any());
    }
    @Test void viewerCannotUseAnyWriteTool() throws Exception {
        TenantMember viewer = new TenantMember(); viewer.setRole(TenantRole.VIEWER);
        when(members.findActiveMember(tenant, userId)).thenReturn(Optional.of(viewer));
        for (String name : List.of("criar_processo", "criar_tarefa", "concluir_tarefa")) {
            JsonNode result = call(name, Map.of());
            assertThat(result.path("isError").asBoolean()).isTrue();
            assertThat(result.path("content").get(0).path("text").asText()).isEqualTo("Seu papel permite apenas leitura");
        }
        verifyNoInteractions(processRepository, tarefaRepository, columns, clientes);
    }
    @Test void memberCanReadProcessInAreaA() throws Exception {
        processInArea(areaA);
        when(tarefas.resumoPorProcesso(any())).thenReturn(Map.of());
        when(movimentos.resumoPorProcesso(any())).thenReturn(Map.of());
        when(tarefas.listByProcesso(processId)).thenReturn(List.of());
        when(links.listByProcesso(processId)).thenReturn(List.of());
        when(movimentos.listByProcesso(processId, 20)).thenReturn(List.of());
        JsonNode result = call("obter_processo", Map.of("processoId", processId));
        assertThat(result.path("isError").asBoolean()).isFalse();
        assertThat(result.path("structuredContent").path("processo").path("id").asText()).isEqualTo(processId.toString());
        assertThat(result.path("structuredContent").path("processo").has("tenantId")).isFalse();
    }
    private BoardColumn validColumn() {
        BoardColumn c = new BoardColumn(); c.setId(UUID.randomUUID()); c.setTenantId(tenant); c.setGroupId(areaA); return c;
    }
    @Test void defaultsToOnlyAuthorizedAreaAndFirstColumnAndFindsClientExactly() throws Exception {
        BoardColumn c = validColumn(); when(columns.findByGroupIdOrderByPosicaoAsc(areaA)).thenReturn(List.of(c));
        Cliente client = new Cliente(); client.setId(UUID.randomUUID());
        when(clienteRepository.findByTenantIdAndNomeIgnoreCase(tenant, "Ana")).thenReturn(Optional.of(client));
        when(clienteRepository.findByIdAndTenantId(client.getId(), tenant)).thenReturn(Optional.of(client));
        when(processRepository.save(any())).thenAnswer(i -> { Processo p = i.getArgument(0); p.setId(UUID.randomUUID()); return p; });
        when(tarefas.resumoPorProcesso(any())).thenReturn(Map.of()); when(movimentos.resumoPorProcesso(any())).thenReturn(Map.of());
        JsonNode result = call("criar_processo", Map.of("clienteNome", " Ana "));
        assertThat(result.path("isError").asBoolean()).as(result.toString()).isFalse();
        var captor = org.mockito.ArgumentCaptor.forClass(Processo.class); verify(processRepository).save(captor.capture());
        assertThat(captor.getValue().getGroupId()).isEqualTo(areaA); assertThat(captor.getValue().getColumnId()).isEqualTo(c.getId());
        assertThat(captor.getValue().getClienteId()).isEqualTo(client.getId()); verify(clientes, never()).create(any());
    }
    @Test void multipleAuthorizedAreasRequireExplicitArea() throws Exception {
        when(groups.resolveGroupRestriction(tenant, userId)).thenReturn(Optional.of(Set.of(areaA, areaB)));
        assertThat(call("criar_processo", Map.of("clienteNome", "Ana")).path("isError").asBoolean()).isTrue();
        verifyNoInteractions(columns, clientes);
    }
    @Test void memberWithNoAreasCannotCreateProcess() throws Exception {
        when(groups.resolveGroupRestriction(tenant, userId)).thenReturn(Optional.of(Set.of()));
        assertThat(call("criar_processo", Map.of("clienteNome", "Ana", "areaId", areaA)).path("isError").asBoolean()).isTrue();
        verifyNoInteractions(columns, clientes, clienteRepository);
    }
    @Test void adminStillCannotCreateInAreaFromAnotherTenant() throws Exception {
        when(groups.resolveGroupRestriction(tenant, userId)).thenReturn(Optional.empty());
        doThrow(com.jurisflow.shared.exception.BusinessException.notFound("Grupo")).when(groups).assertGroupInTenant(areaB, tenant);
        assertThat(call("criar_processo", Map.of("clienteNome", "Ana", "areaId", areaB)).path("isError").asBoolean()).isTrue();
        verifyNoInteractions(columns, clientes, clienteRepository);
    }
    @Test void newClientIsCreatedInCurrentTenantThroughService() throws Exception {
        BoardColumn c = validColumn(); when(columns.findByGroupIdOrderByPosicaoAsc(areaA)).thenReturn(List.of(c));
        UUID clienteId = UUID.randomUUID();
        Cliente client = new Cliente(); client.setId(clienteId); client.setTenantId(tenant);
        when(clienteRepository.findByIdAndTenantId(clienteId, tenant)).thenReturn(Optional.of(client));
        when(clientes.create(any())).thenReturn(new com.jurisflow.modules.cliente.dto.ClienteResponse(
            clienteId, tenant, "Nova Cliente", null, null, null, null, null, null, null, null));
        when(processRepository.save(any())).thenAnswer(i -> { Processo p = i.getArgument(0); p.setId(UUID.randomUUID()); return p; });
        when(tarefas.resumoPorProcesso(any())).thenReturn(Map.of()); when(movimentos.resumoPorProcesso(any())).thenReturn(Map.of());
        assertThat(call("criar_processo", Map.of("clienteNome", "Nova Cliente")).path("isError").asBoolean()).isFalse();
        verify(clientes).create(argThat(request -> request.nome().equals("Nova Cliente")));
        verify(processRepository).save(argThat(p -> tenant.equals(p.getTenantId()) && clienteId.equals(p.getClienteId())));
    }
    @Test void invalidTaskArgumentsNeverReachDomainServices() throws Exception {
        for (Map<String, Object> args : List.of(
            Map.<String, Object>of("processoId", processId, "titulo", "A"),
            Map.<String, Object>of("processoId", processId, "titulo", "Prazo", "prioridade", "MAXIMA"),
            Map.<String, Object>of("processoId", processId, "titulo", "Prazo", "horaPrazo", "25:00"),
            Map.<String, Object>of("processoId", processId, "titulo", "Prazo", "prazo", "2026-02-30")))
            assertThat(call("criar_tarefa", args).path("isError").asBoolean()).isTrue();
        verifyNoInteractions(processRepository, tarefas);
    }
    @Test void columnFromAnotherAreaOrTenantIsRejectedBeforeClientCreation() throws Exception {
        BoardColumn c = validColumn(); c.setGroupId(areaB); when(columns.findById(c.getId())).thenReturn(Optional.of(c));
        assertThat(call("criar_processo", Map.of("clienteNome", "Ana", "colunaId", c.getId())).path("isError").asBoolean()).isTrue();
        c.setGroupId(areaA); c.setTenantId(UUID.randomUUID());
        assertThat(call("criar_processo", Map.of("clienteNome", "Ana", "colunaId", c.getId())).path("isError").asBoolean()).isTrue();
        verifyNoInteractions(clientes, clienteRepository); verify(processRepository, never()).save(any());
    }
}
