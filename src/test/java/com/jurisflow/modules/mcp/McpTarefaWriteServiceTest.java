package com.jurisflow.modules.mcp;

import com.jurisflow.modules.group.GroupService;
import com.jurisflow.modules.mcp.server.McpCallContext;
import com.jurisflow.modules.processo.*;
import com.jurisflow.modules.tarefa.*;
import com.jurisflow.modules.tarefa.dto.TarefaRequest;
import com.jurisflow.security.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class McpTarefaWriteServiceTest {
    final ProcessoRepository processos = mock(ProcessoRepository.class);
    final GroupService groups = mock(GroupService.class);
    final TarefaRepository repository = mock(TarefaRepository.class);
    final TarefaService tarefas = mock(TarefaService.class);
    final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    final TransactionStatus status = new SimpleTransactionStatus();
    final UUID tenant = UUID.randomUUID(), user = UUID.randomUUID(), processId = UUID.randomUUID(), areaA = UUID.randomUUID(), areaB = UUID.randomUUID(), tarefaId = UUID.randomUUID();
    final McpCallContext ctx = new McpCallContext(tenant, new UserPrincipal(user, "user@test", "User", null));
    final TarefaRequest request = new TarefaRequest("Prazo", null, null, null, null);
    McpTarefaWriteService service;

    @BeforeEach void setup() {
        TenantContext.setCurrentTenantId(tenant);
        var proxy = new ProxyFactory(new McpTarefaWriteService(processos, groups, repository, tarefas));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        service = (McpTarefaWriteService) proxy.getProxy();
        when(transactions.getTransaction(any())).thenReturn(status);
        when(groups.resolveGroupRestriction(tenant, user)).thenReturn(Optional.of(Set.of(areaA)));
    }
    @AfterEach void cleanup() { TenantContext.clear(); }
    private void lockedProcess(UUID area) {
        var p = new Processo(); p.setId(processId); p.setTenantId(tenant); p.setGroupId(area);
        when(processos.findByIdAndTenantIdForUpdate(processId, tenant)).thenReturn(Optional.of(p));
    }
    private void task() {
        var t = new Tarefa(); t.setId(tarefaId); t.setProcessoId(processId); t.setTenantId(tenant);
        when(repository.findByIdAndTenantId(tarefaId, tenant)).thenReturn(Optional.of(t));
    }
    @Test void createsOnlyAfterLockAndAreaRevalidationWithinWriteTransaction() {
        lockedProcess(areaA);
        service.create(processId, request, ctx);
        var order = inOrder(transactions, processos, groups, tarefas);
        order.verify(transactions).getTransaction(argThat(def -> !def.isReadOnly()));
        order.verify(processos).findByIdAndTenantIdForUpdate(processId, tenant);
        order.verify(groups).resolveGroupRestriction(tenant, user);
        order.verify(tarefas).create(processId, request, ctx.principal());
        order.verify(transactions).commit(status);
        verify(processos, never()).findByIdAndTenantId(any(), any());
    }
    @Test void rejectsCreateWhenLockedProcessHasMovedToUnauthorizedArea() {
        lockedProcess(areaB);
        assertThatThrownBy(() -> service.create(processId, request, ctx)).hasMessage("Acesso negado");
        verify(processos).findByIdAndTenantIdForUpdate(processId, tenant);
        verifyNoInteractions(tarefas); verify(transactions).rollback(status);
    }
    @Test void completionLoadsTaskLocksProcessRevalidatesAndWritesBeforeCommit() {
        task(); lockedProcess(areaA);
        service.toggleConcluida(tarefaId, true, ctx);
        var order = inOrder(transactions, repository, processos, groups, tarefas);
        order.verify(transactions).getTransaction(any());
        order.verify(repository).findByIdAndTenantId(tarefaId, tenant);
        order.verify(processos).findByIdAndTenantIdForUpdate(processId, tenant);
        order.verify(groups).resolveGroupRestriction(tenant, user);
        order.verify(tarefas).toggleConcluida(tarefaId, true, ctx.principal());
        order.verify(transactions).commit(status);
    }
    @Test void rejectsCompletionWhenLockedProcessHasMovedToUnauthorizedArea() {
        task(); lockedProcess(areaB);
        assertThatThrownBy(() -> service.toggleConcluida(tarefaId, true, ctx)).hasMessage("Acesso negado");
        verify(processos).findByIdAndTenantIdForUpdate(processId, tenant);
        verifyNoInteractions(tarefas); verify(transactions).rollback(status);
    }
    @Test void taskFromOtherTenantCannotReachProcessLock() {
        assertThatThrownBy(() -> service.toggleConcluida(tarefaId, true, ctx)).hasMessageContaining("Tarefa");
        verify(repository).findByIdAndTenantId(tarefaId, tenant);
        verifyNoInteractions(processos, tarefas); verify(transactions).rollback(status);
    }
    @Test void processFromOtherTenantCannotReachWrite() {
        assertThatThrownBy(() -> service.create(processId, request, ctx)).hasMessageContaining("Processo");
        verify(processos).findByIdAndTenantIdForUpdate(processId, tenant);
        verifyNoInteractions(groups, tarefas); verify(transactions).rollback(status);
    }
}
