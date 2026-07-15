package com.jurisflow.modules.processo;

import com.jurisflow.modules.cliente.ClienteRepository;
import com.jurisflow.modules.group.GroupService;
import com.jurisflow.modules.processo.dto.MoveProcessoRequest;
import com.jurisflow.modules.processo.dto.ProcessoRequest;
import com.jurisflow.modules.processo.dto.ProcessoResponse;
import com.jurisflow.modules.tarefa.TarefaService;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessoServiceTest {

    @Mock
    private ProcessoRepository processoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private TarefaService tarefaService;

    @Mock
    private GroupService groupService;

    @InjectMocks
    private ProcessoService processoService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(tenantId);
        principal = new UserPrincipal(userId, "user@test.com", "Test User", null);
        lenient().when(tarefaService.resumoPorProcesso(anyList())).thenReturn(Map.of());
        lenient().when(groupService.resolveGroupRestriction(any(), any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_shouldSaveProcessoWithTenantIdAndCreatedBy() {
        ProcessoRequest request = new ProcessoRequest(
                null, "Descricao", "123", "Civel",
                "1a Vara", "SP", "Sao Paulo", "TJSP", "Reu",
                null, null, null, null
        );

        Processo saved = new Processo();
        saved.setId(UUID.randomUUID());
        saved.setTenantId(tenantId);
        saved.setNumeroProcesso("123");
        saved.setStatus(ProcessoStatus.ATIVO);
        saved.setCreatedBy(userId);

        when(processoRepository.save(any(Processo.class))).thenReturn(saved);

        ProcessoResponse response = processoService.create(request, principal);

        assertThat(response).isNotNull();
        assertThat(response.numeroProcesso()).isEqualTo("123");
        assertThat(response.tenantId()).isEqualTo(tenantId);

        verify(processoRepository).save(argThat(p ->
                p.getTenantId().equals(tenantId) &&
                "123".equals(p.getNumeroProcesso()) &&
                p.getCreatedBy().equals(userId)
        ));
    }

    @Test
    void getById_shouldReturnProcesso_whenFoundInTenant() {
        UUID processoId = UUID.randomUUID();
        Processo processo = new Processo();
        processo.setId(processoId);
        processo.setTenantId(tenantId);
        processo.setNumeroProcesso("Encontrado");
        processo.setStatus(ProcessoStatus.ATIVO);

        when(processoRepository.findByIdAndTenantId(processoId, tenantId))
                .thenReturn(Optional.of(processo));

        ProcessoResponse response = processoService.getById(processoId, userId);
        assertThat(response.numeroProcesso()).isEqualTo("Encontrado");
    }

    @Test
    void getById_shouldThrowNotFound_whenProcessoDoesNotExist() {
        UUID processoId = UUID.randomUUID();
        when(processoRepository.findByIdAndTenantId(processoId, tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> processoService.getById(processoId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Processo")
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listByTenant_shouldFilterByStatusAtivo() {
        Processo p = new Processo();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenantId);
        p.setNumeroProcesso("Ativo");
        p.setStatus(ProcessoStatus.ATIVO);

        when(processoRepository.findByTenantIdAndStatus(eq(tenantId), eq(ProcessoStatus.ATIVO), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p)));

        var page = processoService.listByTenant(Pageable.unpaged(), userId);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).numeroProcesso()).isEqualTo("Ativo");
    }

    @Test
    void delete_shouldArchiveInsteadOfPhysicalDelete() {
        UUID processoId = UUID.randomUUID();
        Processo processo = new Processo();
        processo.setTenantId(tenantId);
        processo.setStatus(ProcessoStatus.ATIVO);

        when(processoRepository.findByIdAndTenantId(processoId, tenantId))
                .thenReturn(Optional.of(processo));
        when(processoRepository.save(any())).thenReturn(processo);

        processoService.delete(processoId);

        verify(processoRepository).save(argThat(p -> p.getStatus() == ProcessoStatus.ARQUIVADO));
        verify(processoRepository, never()).delete(any(Processo.class));
    }

    @Test
    void move_shouldUpdateColumnId() {
        UUID processoId = UUID.randomUUID();
        UUID newColumnId = UUID.randomUUID();

        Processo processo = new Processo();
        processo.setId(processoId);
        processo.setTenantId(tenantId);
        processo.setColumnId(UUID.randomUUID());

        when(processoRepository.findByIdAndTenantId(processoId, tenantId))
                .thenReturn(Optional.of(processo));
        when(processoRepository.save(any())).thenReturn(processo);

        processoService.move(processoId, new MoveProcessoRequest(newColumnId, 2));

        verify(processoRepository).save(argThat(p ->
                p.getColumnId().equals(newColumnId) && p.getPosicaoColuna() == 2
        ));
    }
}
