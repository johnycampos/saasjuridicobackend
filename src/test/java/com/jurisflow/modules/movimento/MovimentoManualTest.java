package com.jurisflow.modules.movimento;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jurisflow.modules.processo.Processo;
import com.jurisflow.modules.processo.ProcessoRepository;
import com.jurisflow.modules.tenant.TenantRepository;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MovimentoManualTest {
    @Test void gravaTextoAutoriaSemChavesExternasENegaOutroTenant() throws Exception {
        var repository = mock(MovimentoRepository.class);
        var processos = mock(ProcessoRepository.class);
        var mapper = new ObjectMapper();
        var service = new MovimentoService(repository, processos, mock(TenantRepository.class),
            mock(JdbcTemplate.class), mapper);
        UUID tenant = UUID.randomUUID(), processo = UUID.randomUUID(), user = UUID.randomUUID();
        var principal = new UserPrincipal(user, "test@test", "Test", null);
        var data = LocalDateTime.of(2026, 10, 11, 14, 30);
        TenantContext.setCurrentTenantId(tenant);
        try {
            when(processos.findByIdAndTenantId(processo, tenant)).thenReturn(Optional.of(new Processo()));
            when(repository.save(any())).thenAnswer(i -> {
                Movimento m = i.getArgument(0);
                assertThat(m.getHashOrigem()).isNull();
                return m;
            });
            var result = service.adicionarManual(processo, " Decisão ", data, "Pedido deferido", principal);
            assertThat(result.tenantId()).isEqualTo(tenant);
            assertThat(result.processoId()).isEqualTo(processo);
            assertThat(result.nome()).isEqualTo("Decisão");
            assertThat(result.dataHora()).isEqualTo(data);
            assertThat(result.codigo()).isNull();
            assertThat(result.visualizado()).isFalse();
            var extras = mapper.readTree(result.dadosExtras());
            assertThat(extras.path("registroManual").asBoolean()).isTrue();
            assertThat(extras.path("registradoPor").asText()).isEqualTo(user.toString());
            assertThat(extras.path("texto").asText()).isEqualTo("Pedido deferido");
            clearInvocations(repository);
            TenantContext.setCurrentTenantId(UUID.randomUUID());
            assertThatThrownBy(() -> service.adicionarManual(processo, "Decisão", data, null, principal))
                .isInstanceOf(BusinessException.class);
            verifyNoInteractions(repository);
        } finally { TenantContext.clear(); }
    }
}
