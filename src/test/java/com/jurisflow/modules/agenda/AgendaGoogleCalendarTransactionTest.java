package com.jurisflow.modules.agenda;

import com.jurisflow.config.*;
import com.jurisflow.modules.audiencia.AudienciaRepository;
import com.jurisflow.modules.cliente.ClienteRepository;
import com.jurisflow.modules.financeiro.*;
import com.jurisflow.modules.googlecalendar.*;
import com.jurisflow.modules.group.GroupService;
import com.jurisflow.modules.processo.ProcessoRepository;
import com.jurisflow.modules.tarefa.*;
import com.jurisflow.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgendaGoogleCalendarTransactionTest {
    enum AgendaEntryPoint { COMPLETA, EVENTOS, RESUMO }
    final UUID tenant = UUID.randomUUID(), userId = UUID.randomUUID();
    final GoogleCalendarConfig config = mock(GoogleCalendarConfig.class);
    final TenantGoogleCalendarRepository googleRepository = mock(TenantGoogleCalendarRepository.class);
    final List<Connection> connections = new ArrayList<>();
    AgendaService agenda;

    @BeforeEach void setup() throws Exception {
        TenantContext.setCurrentTenantId(tenant);
        var dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            var connection = mock(Connection.class);
            when(connection.getAutoCommit()).thenReturn(true);
            connections.add(connection);
            return connection;
        });
        var transactions = new DataSourceTransactionManager(dataSource);
        var google = transactional(new GoogleCalendarService(googleRepository, mock(TokenEncryptionService.class),
            config, mock(TenantAccessGuard.class), mock(RestTemplate.class)), transactions);
        var tarefas = mock(TarefaRepository.class);
        var tarefa = new Tarefa(); tarefa.setId(UUID.randomUUID()); tarefa.setTitulo("Prazo local");
        tarefa.setPrazo(LocalDate.of(2026, 10, 11));
        when(tarefas.findByTenantIdAndPrazoBetween(tenant, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
            .thenAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                return List.of(tarefa);
            });
        var groups = mock(GroupService.class);
        when(groups.resolveGroupRestriction(tenant, userId)).thenReturn(Optional.empty());
        agenda = transactional(new AgendaService(tarefas, mock(ParcelaRepository.class), mock(ContratoRepository.class),
            mock(ClienteRepository.class), mock(ProcessoRepository.class), mock(AudienciaRepository.class), google, groups), transactions);
    }

    private <T> T transactional(T target, DataSourceTransactionManager transactions) {
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        @SuppressWarnings("unchecked") T result = (T) proxy.getProxy();
        return result;
    }

    @AfterEach void cleanup() { TenantContext.clear(); }

    private void assertLocalAgendaReturns(AgendaEntryPoint entryPoint) throws Exception {
        var principal = new UserPrincipal(userId, "user@test", "User", null);
        switch (entryPoint) {
            case COMPLETA -> {
                var result = agenda.getAgendaCompleta(2026, 10, principal);
                assertThat(result.eventos()).extracting(e -> e.titulo()).containsExactly("Prazo local");
                assertThat(result.resumo().totalTarefas()).isEqualTo(1);
                assertThat(result.resumo().totalEventosGoogle()).isZero();
            }
            case EVENTOS -> assertThat(agenda.getEventosMes(2026, 10, principal))
                .extracting(e -> e.titulo()).containsExactly("Prazo local");
            case RESUMO -> {
                var result = agenda.getResumoMes(2026, 10, principal);
                assertThat(result.totalTarefas()).isEqualTo(1);
                assertThat(result.totalEventosGoogle()).isZero();
            }
        }
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(connections).hasSize(1);
        verify(connections.get(0), never()).commit();
        verify(connections.get(0)).rollback();
    }

    @Test void unconfiguredGoogleDoesNotMarkAgendaRollbackOnly() throws Exception {
        assertLocalAgendaReturns(AgendaEntryPoint.COMPLETA);
        verifyNoInteractions(googleRepository);
    }

    @ParameterizedTest
    @EnumSource(AgendaEntryPoint.class)
    void googleBusinessExceptionDoesNotBreakAnyAgendaEntryPoint(AgendaEntryPoint entryPoint) throws Exception {
        when(config.isConfigured()).thenReturn(true);
        when(googleRepository.findByTenantId(tenant)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return Optional.empty();
        });
        assertLocalAgendaReturns(entryPoint);
    }

    @ParameterizedTest
    @EnumSource(AgendaEntryPoint.class)
    void googleRuntimeExceptionDoesNotBreakAnyAgendaEntryPoint(AgendaEntryPoint entryPoint) throws Exception {
        when(config.isConfigured()).thenReturn(true);
        when(googleRepository.findByTenantId(tenant)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            throw new IllegalStateException("Google indisponivel");
        });
        assertLocalAgendaReturns(entryPoint);
    }
}
