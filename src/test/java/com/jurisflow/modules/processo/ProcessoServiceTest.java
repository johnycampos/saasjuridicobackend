package com.jurisflow.modules.processo;

import com.jurisflow.modules.board.BoardColumnRepository;
import com.jurisflow.modules.cliente.ClienteRepository;
import com.jurisflow.modules.group.GroupRepository;
import com.jurisflow.modules.group.GroupService;
import com.jurisflow.modules.movimento.MovimentoService;
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

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private BoardColumnRepository boardColumnRepository;

    @Mock
    private MovimentoService movimentoService;

    @Mock private com.jurisflow.security.TenantAccessGuard tenantAccessGuard;

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
        lenient().when(movimentoService.resumoPorProcesso(anyList())).thenReturn(Map.of());
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
        processo.setId(processoId);
        processo.setTenantId(tenantId);
        processo.setStatus(ProcessoStatus.ATIVO);

        when(processoRepository.findByIdAndTenantIdForUpdate(processoId, tenantId))
                .thenReturn(Optional.of(processo));
        when(processoRepository.save(any())).thenReturn(processo);

        processoService.delete(processoId, principal);

        verify(processoRepository).save(argThat(p -> p.getStatus() == ProcessoStatus.ARQUIVADO));
        verify(processoRepository, never()).delete(any(Processo.class));
    }

    private Processo locked(ProcessoStatus status) {
        var p = new Processo(); p.setId(UUID.randomUUID()); p.setTenantId(tenantId);
        p.setGroupId(UUID.randomUUID()); p.setStatus(status);
        when(processoRepository.findByIdAndTenantIdForUpdate(p.getId(), tenantId)).thenReturn(Optional.of(p));
        return p;
    }

    @Test void archiveRejectsViewerBeforeWriting() {
        var id = UUID.randomUUID();
        doThrow(BusinessException.forbidden()).when(tenantAccessGuard).requireMinRole(tenantId, userId, com.jurisflow.modules.tenant.TenantRole.MEMBER);
        assertThatThrownBy(() -> processoService.archive(id, principal)).hasMessage("Acesso negado");
        verifyNoInteractions(processoRepository);
    }

    @Test void archiveRejectsMemberOutsideAreaUnderLock() {
        var p = locked(ProcessoStatus.ATIVO);
        when(groupService.resolveGroupRestriction(tenantId, userId)).thenReturn(Optional.of(java.util.Set.of(UUID.randomUUID())));
        assertThatThrownBy(() -> processoService.archive(p.getId(), principal)).hasMessage("Acesso negado");
        verify(processoRepository, never()).save(any());
    }

    @Test void archiveAllowsMemberInAreaAndIsIdempotent() {
        var p = locked(ProcessoStatus.ATIVO);
        when(groupService.resolveGroupRestriction(tenantId, userId)).thenReturn(Optional.of(java.util.Set.of(p.getGroupId())));
        when(processoRepository.save(p)).thenReturn(p);
        assertThat(processoService.archive(p.getId(), principal).status()).isEqualTo(ProcessoStatus.ARQUIVADO);
        assertThat(processoService.archive(p.getId(), principal).status()).isEqualTo(ProcessoStatus.ARQUIVADO);
        verify(processoRepository, times(1)).save(p);
        var order = inOrder(processoRepository, tenantAccessGuard, groupService);
        order.verify(tenantAccessGuard).requireMinRole(tenantId, userId, com.jurisflow.modules.tenant.TenantRole.MEMBER);
        order.verify(processoRepository).findByIdAndTenantIdForUpdate(p.getId(), tenantId);
        order.verify(groupService).resolveGroupRestriction(tenantId, userId);
        order.verify(processoRepository).save(p);
    }

    @Test void unarchiveRecoversFirstColumnWhenOriginalIsMissing() {
        var p = locked(ProcessoStatus.ARQUIVADO);
        var column = new com.jurisflow.modules.board.BoardColumn(); column.setId(UUID.randomUUID());
        column.setTenantId(tenantId); column.setGroupId(p.getGroupId());
        when(boardColumnRepository.findByGroupIdOrderByPosicaoAsc(p.getGroupId())).thenReturn(List.of(column));
        when(processoRepository.save(p)).thenReturn(p);
        var response = processoService.unarchive(p.getId(), principal);
        assertThat(response.status()).isEqualTo(ProcessoStatus.ATIVO);
        assertThat(response.columnId()).isEqualTo(column.getId());
        assertThat(response.posicaoColuna()).isZero();
    }

    @Test void unarchiveRejectsAreaWithoutColumns() {
        var p = locked(ProcessoStatus.ARQUIVADO);
        assertThatThrownBy(() -> processoService.unarchive(p.getId(), principal)).hasMessage("A área do processo não possui nenhuma coluna")
            .extracting("status").isEqualTo(HttpStatus.CONFLICT);
        verify(processoRepository, never()).save(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"deleted", "otherArea", "otherTenant"})
    void unarchiveRecoversColumnWhenOldColumnIsNoLongerUsable(String reason) {
        var p = locked(ProcessoStatus.ARQUIVADO); p.setColumnId(UUID.randomUUID());
        UUID originalColumn = p.getColumnId();
        if (reason.equals("otherArea")) {
            var old = new com.jurisflow.modules.board.BoardColumn(); old.setId(p.getColumnId());
            old.setTenantId(tenantId);
            old.setGroupId(UUID.randomUUID());
            when(boardColumnRepository.findByIdAndTenantId(p.getColumnId(), tenantId)).thenReturn(Optional.of(old));
        }
        var first = new com.jurisflow.modules.board.BoardColumn(); first.setId(UUID.randomUUID());
        first.setTenantId(tenantId); first.setGroupId(p.getGroupId());
        when(boardColumnRepository.findByGroupIdOrderByPosicaoAsc(p.getGroupId())).thenReturn(List.of(first));
        when(processoRepository.save(p)).thenReturn(p);
        assertThat(processoService.unarchive(p.getId(), principal).columnId()).isEqualTo(first.getId());
        verify(boardColumnRepository).findByIdAndTenantId(originalColumn, tenantId);
    }

    @Test void unarchiveRejectsViewerAndUnauthorizedArea() {
        var p = locked(ProcessoStatus.ARQUIVADO);
        doThrow(BusinessException.forbidden()).when(tenantAccessGuard).requireMinRole(tenantId, userId, com.jurisflow.modules.tenant.TenantRole.MEMBER);
        assertThatThrownBy(() -> processoService.unarchive(p.getId(), principal)).hasMessage("Acesso negado");
        verify(processoRepository, never()).findByIdAndTenantIdForUpdate(any(), any());
        reset(tenantAccessGuard);
        when(groupService.resolveGroupRestriction(tenantId, userId)).thenReturn(Optional.of(java.util.Set.of(UUID.randomUUID())));
        assertThatThrownBy(() -> processoService.unarchive(p.getId(), principal)).hasMessage("Acesso negado");
        verify(processoRepository, never()).save(any());
        verifyNoInteractions(boardColumnRepository);
    }

    @Test void unarchivePreservesExistingColumnInSameTenantAndArea() {
        var p = locked(ProcessoStatus.ARQUIVADO); p.setColumnId(UUID.randomUUID());
        var column = new com.jurisflow.modules.board.BoardColumn(); column.setId(p.getColumnId());
        column.setTenantId(tenantId); column.setGroupId(p.getGroupId());
        when(boardColumnRepository.findByIdAndTenantId(p.getColumnId(), tenantId)).thenReturn(Optional.of(column));
        when(processoRepository.save(p)).thenReturn(p);
        assertThat(processoService.unarchive(p.getId(), principal).columnId()).isEqualTo(column.getId());
        var order = inOrder(tenantAccessGuard, processoRepository, groupService, boardColumnRepository);
        order.verify(tenantAccessGuard).requireMinRole(tenantId, userId, com.jurisflow.modules.tenant.TenantRole.MEMBER);
        order.verify(processoRepository).findByIdAndTenantIdForUpdate(p.getId(), tenantId);
        order.verify(groupService).resolveGroupRestriction(tenantId, userId);
        order.verify(boardColumnRepository).findByIdAndTenantId(p.getColumnId(), tenantId);
        verify(boardColumnRepository, never()).findByGroupIdOrderByPosicaoAsc(any());
    }

    @Test void archivedListAndSearchRespectAreaAndNewestUpdateOrder() {
        var groups = java.util.Set.of(UUID.randomUUID());
        when(groupService.resolveGroupRestriction(tenantId, userId)).thenReturn(Optional.of(groups));
        var pageable = org.springframework.data.domain.PageRequest.of(2, 10, org.springframework.data.domain.Sort.by("updatedAt").descending());
        when(processoRepository.findByTenantIdAndStatusAndGroupIdIn(tenantId, ProcessoStatus.ARQUIVADO, groups, pageable)).thenReturn(new PageImpl<>(List.of()));
        when(processoRepository.searchArquivadosInGroups(tenantId, "Ana", groups, pageable)).thenReturn(new PageImpl<>(List.of()));
        processoService.listArquivados(null, pageable, userId);
        processoService.listArquivados("Ana", pageable, userId);
        verify(processoRepository).findByTenantIdAndStatusAndGroupIdIn(tenantId, ProcessoStatus.ARQUIVADO, groups, pageable);
        verify(processoRepository).searchArquivadosInGroups(tenantId, "Ana", groups, pageable);
        verify(processoRepository, never()).searchArquivados(any(), any(), any());
    }

    @Test void listByGroupUsesActiveStatus() {
        var group = UUID.randomUUID();
        when(processoRepository.findByTenantIdAndGroupIdAndStatus(tenantId, group, ProcessoStatus.ATIVO, Pageable.unpaged())).thenReturn(new PageImpl<>(List.of()));
        assertThat(processoService.listByGroup(group, Pageable.unpaged(), userId)).isEmpty();
        verify(processoRepository).findByTenantIdAndGroupIdAndStatus(tenantId, group, ProcessoStatus.ATIVO, Pageable.unpaged());
    }

    @Test void archivedProcessCannotMoveOnBoardOrToAnotherArea() {
        var p = new Processo(); p.setId(UUID.randomUUID()); p.setTenantId(tenantId); p.setStatus(ProcessoStatus.ARQUIVADO);
        when(processoRepository.findByIdAndTenantIdForUpdate(p.getId(), tenantId)).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> processoService.move(p.getId(), new MoveProcessoRequest(UUID.randomUUID(), 0))).hasMessage("Processo arquivado");
        assertThatThrownBy(() -> processoService.moveToGroup(p.getId(), UUID.randomUUID(), principal)).hasMessage("Processo arquivado");
        verify(processoRepository, never()).save(any());
    }

    @Test
    void move_shouldUpdateColumnId() {
        UUID processoId = UUID.randomUUID();
        UUID newColumnId = UUID.randomUUID();

        Processo processo = new Processo();
        processo.setId(processoId);
        processo.setTenantId(tenantId);
        processo.setColumnId(UUID.randomUUID());

        when(processoRepository.findByIdAndTenantIdForUpdate(processoId, tenantId))
                .thenReturn(Optional.of(processo));
        when(processoRepository.save(any())).thenReturn(processo);

        processoService.move(processoId, new MoveProcessoRequest(newColumnId, 2));

        verify(processoRepository).save(argThat(p ->
                p.getColumnId().equals(newColumnId) && p.getPosicaoColuna() == 2
        ));
    }

    @Test
    void listParaSincronizacaoMovimentos_shouldMapParaProcessoNumeroResponse() {
        Processo processo = new Processo();
        processo.setId(UUID.randomUUID());
        processo.setTenantId(tenantId);
        processo.setNumeroProcesso("5006378-15.2024.4.02.5120");
        processo.setTribunal("TRF2");
        processo.setStatus(ProcessoStatus.ATIVO);

        Pageable pageable = Pageable.unpaged();
        when(processoRepository.findAtivosComNumeroProcesso(ProcessoStatus.ATIVO, null, pageable))
                .thenReturn(new PageImpl<>(List.of(processo)));

        var page = processoService.listParaSincronizacaoMovimentos(null, pageable);

        assertThat(page.getContent()).hasSize(1);
        var item = page.getContent().get(0);
        assertThat(item.tenantId()).isEqualTo(tenantId);
        assertThat(item.processoId()).isEqualTo(processo.getId());
        assertThat(item.numeroProcesso()).isEqualTo("5006378-15.2024.4.02.5120");
        assertThat(item.tribunal()).isEqualTo("TRF2");
    }

    @Test
    void listParaSincronizacaoMovimentos_shouldRepassarFiltroDeTenant() {
        Pageable pageable = Pageable.unpaged();
        when(processoRepository.findAtivosComNumeroProcesso(ProcessoStatus.ATIVO, tenantId, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        processoService.listParaSincronizacaoMovimentos(tenantId, pageable);

        verify(processoRepository).findAtivosComNumeroProcesso(ProcessoStatus.ATIVO, tenantId, pageable);
    }
}
