package com.jurisflow.modules.mcp;

import com.fasterxml.jackson.databind.*;
import com.jurisflow.modules.board.BoardColumnRepository;
import com.jurisflow.modules.cliente.*;
import com.jurisflow.modules.group.*;
import com.jurisflow.modules.mcp.server.*;
import com.jurisflow.modules.mcp.server.tools.*;
import com.jurisflow.modules.movimento.*;
import com.jurisflow.modules.processo.*;
import com.jurisflow.modules.processolink.ProcessoLinkService;
import com.jurisflow.modules.tarefa.TarefaService;
import com.jurisflow.modules.tenant.TenantRepository;
import com.jurisflow.security.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class McpToolsPaginationTest {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final UUID tenant = UUID.randomUUID(), userId = UUID.randomUUID(), processId = UUID.randomUUID(), area = UUID.randomUUID();
    final UserPrincipal user = new UserPrincipal(userId, "user@test", "User", null);
    final ProcessoRepository processRepository = mock(ProcessoRepository.class);
    final MovimentoRepository movementRepository = mock(MovimentoRepository.class);
    final ClienteRepository clientRepository = mock(ClienteRepository.class);
    final GroupService groups = mock(GroupService.class);
    final TarefaService tarefas = mock(TarefaService.class);
    final ProcessoLinkService links = mock(ProcessoLinkService.class);
    final MovimentoService movimentos = new MovimentoService(movementRepository, processRepository, mock(TenantRepository.class), mock(JdbcTemplate.class), mapper);
    final ClienteService clientes = new ClienteService(clientRepository);
    final com.jurisflow.modules.audiencia.AudienciaService audiencias = mock(com.jurisflow.modules.audiencia.AudienciaService.class);
    final ProcessoService processos = new ProcessoService(processRepository, clientRepository, tarefas, movimentos, groups, mock(GroupRepository.class), mock(BoardColumnRepository.class), mock(TenantAccessGuard.class));

    @BeforeEach void setup() {
        TenantContext.setCurrentTenantId(tenant);
        var process = new Processo(); process.setId(processId); process.setTenantId(tenant); process.setGroupId(area);
        when(processRepository.findByIdAndTenantId(processId, tenant)).thenReturn(Optional.of(process));
        when(groups.resolveGroupRestriction(tenant, userId)).thenReturn(Optional.of(Set.of(area)));
    }
    @AfterEach void cleanup() { TenantContext.clear(); }
    private JsonNode call(McpTool tool, Map<String, Object> args) throws Exception {
        var controller = new McpController(mapper, List.of(tool), mock(TenantAccessGuard.class));
        String request = mapper.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/call",
            "params", Map.of("name", tool.name(), "arguments", args)));
        return mapper.valueToTree(controller.handle(request, user).getBody()).path("result");
    }
    private void assertSuccess(JsonNode result) { assertThat(result.path("isError").asBoolean()).as(result.toString()).isFalse(); }

    @Test void movementDefaultsToTwentyInDatabase() throws Exception {
        assertSuccess(call(new ListarMovimentacoesTool(processos, movimentos), Map.of("processoId", processId)));
        verify(movementRepository).findByTenantIdAndProcessoIdOrderByDataHoraDesc(tenant, processId, PageRequest.of(0, 20));
        verify(movementRepository, never()).findByProcessoIdOrderByDataHoraDesc(any());
    }
    @Test void movementMaximumIsOneHundredInDatabase() throws Exception {
        assertSuccess(call(new ListarMovimentacoesTool(processos, movimentos), Map.of("processoId", processId, "limite", 100)));
        verify(movementRepository).findByTenantIdAndProcessoIdOrderByDataHoraDesc(tenant, processId, PageRequest.of(0, 100));
    }
    @Test void oversizedMovementLimitIsRejectedBeforeQueries() throws Exception {
        assertThat(call(new ListarMovimentacoesTool(processos, movimentos), Map.of("processoId", processId, "limite", 101))
            .path("isError").asBoolean()).isTrue();
        verifyNoInteractions(processRepository, movementRepository);
    }
    @Test void processDetailsFetchOnlyLatestTwentyMovementsInDatabase() throws Exception {
        assertSuccess(call(new ObterProcessoTool(processos, tarefas, links, movimentos, audiencias), Map.of("processoId", processId)));
        verify(movementRepository).findByTenantIdAndProcessoIdOrderByDataHoraDesc(tenant, processId, PageRequest.of(0, 20));
        verify(movementRepository, never()).findByProcessoIdOrderByDataHoraDesc(any());
        verify(movementRepository).aggregateResumoPorProcesso(List.of(processId));
        verify(movementRepository, never()).findByProcessoIdIn(any());
    }
    @Test void clientsDefaultToPagedDatabaseQuery() throws Exception {
        var page = PageRequest.of(0, 20);
        when(clientRepository.findByTenantIdOrderByNomeAsc(tenant, page)).thenReturn(Page.empty(page));
        var result = call(new BuscarClientesTool(clientes), Map.of()); assertSuccess(result);
        verify(clientRepository).findByTenantIdOrderByNomeAsc(tenant, page);
        assertThat(result.path("structuredContent").path("tamanho").asInt()).isEqualTo(20);
    }
    @Test void clientsUseRequestedPageAndBoundedSizeWithSearchInDatabase() throws Exception {
        var page = PageRequest.of(2, 50);
        when(clientRepository.findByTenantIdAndNomeContainingIgnoreCaseOrderByNomeAsc(tenant, "Ana", page)).thenReturn(Page.empty(page));
        assertSuccess(call(new BuscarClientesTool(clientes), Map.of("busca", "Ana", "pagina", 2, "tamanho", 50)));
        verify(clientRepository).findByTenantIdAndNomeContainingIgnoreCaseOrderByNomeAsc(tenant, "Ana", page);
    }
    @Test void clientsRejectOversizedPagesBeforeQueries() throws Exception {
        assertThat(call(new BuscarClientesTool(clientes), Map.of("tamanho", 51)).path("isError").asBoolean()).isTrue();
        verifyNoInteractions(clientRepository);
    }
    @Test void areaAndSearchUseCombinedPagedQuery() throws Exception {
        var page = PageRequest.of(3, 20);
        when(processRepository.searchByGroup(tenant, area, "Ana", page)).thenReturn(Page.empty(page));
        assertSuccess(call(new ListarProcessosTool(processos, groups), Map.of("areaId", area, "busca", "Ana", "pagina", 3)));
        verify(processRepository).searchByGroup(tenant, area, "Ana", page);
        verify(groups).assertGroupInTenant(area, tenant);
        verify(processRepository, never()).findByTenantIdAndGroupIdAndStatus(any(), any(), any(), any());
    }
    @Test void combinedSearchInUnauthorizedAreaDoesNotQueryDatabase() throws Exception {
        assertThat(call(new ListarProcessosTool(processos, groups), Map.of("areaId", UUID.randomUUID(), "busca", "Ana"))
            .path("isError").asBoolean()).isTrue();
        verifyNoInteractions(processRepository);
    }

    @Test void foreignTenantAreaIsRejectedForUnrestrictedUsers() throws Exception {
        when(groups.resolveGroupRestriction(tenant, userId)).thenReturn(Optional.empty());
        var foreignArea = UUID.randomUUID();
        doThrow(new com.jurisflow.shared.exception.BusinessException("Area invalida"))
            .when(groups).assertGroupInTenant(foreignArea, tenant);
        for (var args : List.of(Map.<String, Object>of("areaId", foreignArea),
                Map.<String, Object>of("areaId", foreignArea, "busca", "Ana"))) {
            assertThat(call(new ListarProcessosTool(processos, groups), args).path("isError").asBoolean()).isTrue();
        }
        verifyNoInteractions(processRepository);
    }
}
