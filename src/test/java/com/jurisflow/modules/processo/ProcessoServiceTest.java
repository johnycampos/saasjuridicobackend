package com.jurisflow.modules.processo;

import com.jurisflow.modules.processo.dto.MoveProcessoRequest;
import com.jurisflow.modules.processo.dto.ProcessoRequest;
import com.jurisflow.modules.processo.dto.ProcessoResponse;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessoServiceTest {

    @Mock
    private ProcessoRepository processoRepository;

    @InjectMocks
    private ProcessoService processoService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(tenantId);
        principal = new UserPrincipal(userId, "user@test.com", "Test User", null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_shouldSaveProcessoWithTenantIdAndCreatedBy() {
        ProcessoRequest request = new ProcessoRequest(
                "Processo de Teste", "Descricao", "123", "Civel",
                "1a Vara", "SP", "TJSP", "Autor", "Reu",
                PrioridadeTipo.ALTA, null, null, null, null, null
        );

        Processo saved = new Processo();
        saved.setTenantId(tenantId);
        saved.setTitulo("Processo de Teste");
        saved.setPrioridade(PrioridadeTipo.ALTA);
        saved.setStatus(ProcessoStatus.ATIVO);
        saved.setCreatedBy(userId);

        when(processoRepository.save(any(Processo.class))).thenReturn(saved);

        ProcessoResponse response = processoService.create(request, principal);

        assertThat(response).isNotNull();
        assertThat(response.titulo()).isEqualTo("Processo de Teste");
        assertThat(response.tenantId()).isEqualTo(tenantId);

        verify(processoRepository).save(argThat(p ->
                p.getTenantId().equals(tenantId) &&
                p.getTitulo().equals("Processo de Teste") &&
                p.getCreatedBy().equals(userId) &&
                p.getPrioridade() == PrioridadeTipo.ALTA
        ));
    }

    @Test
    void create_shouldUseDefaultPriority_whenPriorityIsNull() {
        ProcessoRequest request = new ProcessoRequest(
                "Processo", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null
        );

        Processo saved = new Processo();
        saved.setTenantId(tenantId);
        saved.setTitulo("Processo");
        saved.setPrioridade(PrioridadeTipo.MEDIA);
        saved.setStatus(ProcessoStatus.ATIVO);

        when(processoRepository.save(any())).thenReturn(saved);

        processoService.create(request, principal);

        verify(processoRepository).save(argThat(p -> p.getPrioridade() == PrioridadeTipo.MEDIA));
    }

    @Test
    void getById_shouldReturnProcesso_whenFoundInTenant() {
        UUID processoId = UUID.randomUUID();
        Processo processo = new Processo();
        processo.setTenantId(tenantId);
        processo.setTitulo("Encontrado");
        processo.setStatus(ProcessoStatus.ATIVO);

        when(processoRepository.findByIdAndTenantId(processoId, tenantId))
                .thenReturn(Optional.of(processo));

        ProcessoResponse response = processoService.getById(processoId);
        assertThat(response.titulo()).isEqualTo("Encontrado");
    }

    @Test
    void getById_shouldThrowNotFound_whenProcessoDoesNotExist() {
        UUID processoId = UUID.randomUUID();
        when(processoRepository.findByIdAndTenantId(processoId, tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> processoService.getById(processoId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Processo")
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listByTenant_shouldFilterByStatusAtivo() {
        Processo p = new Processo();
        p.setTenantId(tenantId);
        p.setTitulo("Ativo");
        p.setStatus(ProcessoStatus.ATIVO);

        when(processoRepository.findByTenantIdAndStatus(eq(tenantId), eq(ProcessoStatus.ATIVO), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p)));

        var page = processoService.listByTenant(Pageable.unpaged());

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).titulo()).isEqualTo("Ativo");
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
