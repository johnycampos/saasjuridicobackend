package com.jurisflow.modules.processo;

import com.jurisflow.modules.board.*;
import com.jurisflow.modules.cliente.ClienteRepository;
import com.jurisflow.modules.group.GroupService;
import com.jurisflow.modules.movimento.MovimentoService;
import com.jurisflow.modules.tarefa.*;
import com.jurisflow.modules.audiencia.AudienciaRepository;
import com.jurisflow.modules.financeiro.ParcelaRepository;
import com.jurisflow.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArquivadosVisibilityTest {
    @Test void boardQueriesOnlyActiveProcessesInCurrentTenant() {
        var tenant = UUID.randomUUID(); var group = UUID.randomUUID(); var user = UUID.randomUUID();
        var columns = mock(BoardColumnRepository.class); var processos = mock(ProcessoRepository.class);
        var groups = mock(GroupService.class); var column = new BoardColumn(); column.setId(UUID.randomUUID());
        when(groups.resolveGroupRestriction(tenant, user)).thenReturn(Optional.empty());
        when(columns.findByGroupIdOrderByPosicaoAsc(group)).thenReturn(List.of(column));
        TenantContext.setCurrentTenantId(tenant);
        try {
            var service = new BoardService(columns, processos, mock(ClienteRepository.class), mock(TarefaService.class),
                mock(MovimentoService.class), groups, mock(TenantAccessGuard.class));
            assertThat(service.getBoardByGroup(group, user)).singleElement().satisfies(c -> assertThat(c.processos()).isEmpty());
            verify(processos).findByTenantIdAndColumnIdAndStatusOrderByPosicaoColunaAsc(tenant, column.getId(), ProcessoStatus.ATIVO);
        } finally { TenantContext.clear(); }
    }

    @Test void searchAgendaAndGeneralAudienceQueriesExcludeArchivedProcesses() {
        for (var repository : List.of(ProcessoRepository.class, TarefaRepository.class, ParcelaRepository.class, AudienciaRepository.class)) {
            for (var method : repository.getDeclaredMethods()) {
                var name = method.getName();
                if (!(name.equals("search") || name.equals("searchByGroup") || name.equals("searchInGroups")
                    || name.contains("Between") || name.equals("findByTenantIdAndStatusNotOrderByDataAscHoraAsc"))) continue;
                assertThat(method.getAnnotation(Query.class)).as(repository.getSimpleName() + "." + name).isNotNull();
                var query = method.getAnnotation(Query.class).value();
                assertThat(query).contains("tenantId = :tenantId", "ProcessoStatus.ATIVO");
                if (name.contains("InGroups")) assertThat(query).contains("groupId IN :groupIds");
            }
        }
    }
}
