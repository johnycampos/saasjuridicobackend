package com.jurisflow.modules.importacao;

import com.jurisflow.modules.board.*;
import com.jurisflow.modules.cliente.*;
import com.jurisflow.modules.group.*;
import com.jurisflow.modules.processo.ProcessoService;
import com.jurisflow.modules.processo.dto.ProcessoRequest;
import com.jurisflow.security.*;
import com.jurisflow.shared.exception.BusinessException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {
    @Mock BoardColumnRepository columns;
    @Mock GroupRepository groups;
    @Mock GroupService groupService;
    @Mock ClienteRepository clients;
    @Mock ProcessoService processos;
    @InjectMocks ImportService service;
    final UUID tenant = UUID.randomUUID(), area = UUID.randomUUID(), columnId = UUID.randomUUID(), clientId = UUID.randomUUID();
    final UserPrincipal user = new UserPrincipal(UUID.randomUUID(), "test@test", "Test", null);
    final String[] header = {"Numero processo", "Cliente", "Tipo de Ação", "Vara", "Comarca", "Réu", "Descrição"};

    @BeforeEach void setup() {
        TenantContext.setCurrentTenantId(tenant);
        when(groups.findByIdAndTenantId(area, tenant)).thenReturn(Optional.of(new Group()));
        when(groupService.resolveGroupRestriction(tenant, user.getId())).thenReturn(Optional.empty());
        var column = new BoardColumn(); column.setId(columnId); column.setNome("Geral");
        when(columns.findByGroupIdOrderByPosicaoAsc(area)).thenReturn(List.of(column));
        lenient().when(clients.save(any())).thenAnswer(invocation -> {
            Cliente client = invocation.getArgument(0); client.setId(clientId); return client;
        });
    }
    @AfterEach void cleanup() { TenantContext.clear(); }
    MockMultipartFile workbook(String[] headers, String[]... rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Geral");
            var row = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) row.createCell(c).setCellValue(headers[c]);
            for (int r = 0; r < rows.length; r++) {
                row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) row.createCell(c).setCellValue(rows[r][c]);
            }
            workbook.write(bytes);
            return new MockMultipartFile("file", "processos.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray());
        }
    }

    @Test void missingClientIsCreatedInTenantAndUsedByProcess() throws Exception {
        var result = service.importar(area, workbook(header, new String[]{"123", "Ana"}), user);
        assertThat(result.processosCriados()).isEqualTo(1); assertThat(result.clientesCriados()).isEqualTo(1);
        verify(clients).save(argThat(c -> c.getTenantId().equals(tenant) && c.getNome().equals("Ana")));
        verify(processos).create(argThat(p -> p.clienteId().equals(clientId) && p.groupId().equals(area) && p.columnId().equals(columnId)), eq(user));
    }

    @Test void existingClientIsReusedIgnoringCaseAndWhitespaceIncludingNbsp() throws Exception {
        var client = new Cliente(); client.setId(clientId);
        when(clients.findFirstByTenantIdAndNomeIgnoreCaseOrderByCreatedAtAsc(tenant, "aNA Silva")).thenReturn(Optional.of(client));
        var result = service.importar(area, workbook(header, new String[]{"123", "  aNA\u00a0  Silva  "}), user);
        assertThat(result.clientesCriados()).isZero();
        verify(clients, never()).save(any());
        verify(processos).create(argThat(p -> p.clienteId().equals(clientId)), eq(user));
    }

    @Test void duplicateNamesUseOldestClientWithoutSingleResultLookup() throws Exception {
        var oldest = new Cliente(); oldest.setId(UUID.randomUUID());
        when(clients.findFirstByTenantIdAndNomeIgnoreCaseOrderByCreatedAtAsc(tenant, "Ana")).thenReturn(Optional.of(oldest));
        assertThat(service.importar(area, workbook(header, new String[]{"123", "Ana"}), user).processosCriados()).isEqualTo(1);
        verify(processos).create(argThat(p -> p.clienteId().equals(oldest.getId())), eq(user));
        verify(clients, never()).findByTenantIdAndNomeIgnoreCase(any(), any());
        verify(clients, never()).save(any());
    }

    @Test void repeatedNewClientIsCreatedOnlyOnceUsingNormalizedCache() throws Exception {
        var result = service.importar(area, workbook(header, new String[]{"123", "Ana Silva"}, new String[]{"456", "  ANA\u00a0  SILVA "}), user);
        assertThat(result.processosCriados()).isEqualTo(2); assertThat(result.clientesCriados()).isEqualTo(1);
        verify(clients, times(1)).save(any());
        verify(clients, times(1)).findFirstByTenantIdAndNomeIgnoreCaseOrderByCreatedAtAsc(any(), any());
        verify(processos, times(2)).create(argThat(p -> p.clienteId().equals(clientId)), eq(user));
    }

    @Test void reorderedHeadersWithUnknownColumnsAndAccentsMapCorrectly() throws Exception {
        var headers = new String[]{"Ignorar", " Réu ", "CLIENTE", "Número do Processo", "Tipo  de Ação", "Descrição", "Vara", "Comarca"};
        service.importar(area, workbook(headers, new String[]{"outro", "Réu A", "Ana", "123", "Cível", "Texto", "Vara A", "Comarca A"}), user);
        var capture = ArgumentCaptor.forClass(ProcessoRequest.class); verify(processos).create(capture.capture(), eq(user));
        var p = capture.getValue();
        assertThat(p.numeroProcesso()).isEqualTo("123"); assertThat(p.reu()).isEqualTo("Réu A");
        assertThat(p.tipoAcao()).isEqualTo("Cível"); assertThat(p.descricao()).isEqualTo("Texto");
        assertThat(p.vara()).isEqualTo("Vara A"); assertThat(p.comarca()).isEqualTo("Comarca A");
    }

    @Test void oversizedFieldSkipsOnlyInvalidRowBeforeCreatingClient() throws Exception {
        var result = service.importar(area, workbook(header,
            new String[]{"123", "Cliente inválido", "Cível", "x".repeat(256)}, new String[]{"456", "Ana"}), user);
        assertThat(result.processosCriados()).isEqualTo(1); assertThat(result.clientesCriados()).isEqualTo(1);
        assertThat(result.erros()).singleElement().asString().contains("Geral", "linha 2", "Vara excede 255");
        verify(clients, never()).findFirstByTenantIdAndNomeIgnoreCaseOrderByCreatedAtAsc(tenant, "Cliente inválido");
        verify(processos, times(1)).create(any(), eq(user));
    }

    @Test void allBoundedFieldsAreValidatedBeforePersistence() throws Exception {
        var result = service.importar(area, workbook(header, new String[]{"x".repeat(51), "x".repeat(256), "x".repeat(256), "x".repeat(256), "x".repeat(256), "x".repeat(501)}), user);
        assertThat(result.erros()).hasSize(6); assertThat(result.clientesCriados()).isZero();
        verifyNoInteractions(clients, processos);
    }

    @Test void unknownHeadersFallBackToOriginalPositions() throws Exception {
        var result = service.importar(area, workbook(new String[]{"A", "B"}, new String[]{"123", "Ana"}), user);
        assertThat(result.processosCriados()).isEqualTo(1);
        assertThat(result.avisos()).isEmpty();
        verify(processos).create(argThat(p -> "123".equals(p.numeroProcesso()) && clientId.equals(p.clienteId())), eq(user));
    }

    @Test void blankClientIsRowError() throws Exception {
        var result = service.importar(area, workbook(header, new String[]{"123", "\u00a0 "}), user);
        assertThat(result.erros()).singleElement().asString().contains("Cliente é obrigatório");
        verifyNoInteractions(clients, processos);
    }

    @Test void businessExceptionFromProcessIsPreserved() throws Exception {
        var failure = BusinessException.forbidden(); when(processos.create(any(), eq(user))).thenThrow(failure);
        var file = workbook(header, new String[]{"123", "Ana"});
        assertThatThrownBy(() -> service.importar(area, file, user)).isSameAs(failure);
    }

    @Test void unexpectedPersistenceErrorDoesNotLeakDetailsOrClaimFileIsUnreadable() throws Exception {
        when(processos.create(any(), eq(user))).thenThrow(new IllegalStateException("internal jdbc detail"));
        var file = workbook(header, new String[]{"123", "Ana"});
        assertThatThrownBy(() -> service.importar(area, file, user)).hasMessage("Não foi possível importar a planilha. Tente novamente.");
    }

    @Test void invalidFileReportsReadingErrorWithoutInternals() {
        var file = new MockMultipartFile("file", "invalid.xlsx", "application/octet-stream", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.importar(area, file, user)).hasMessageContaining("planilha válida");
        verifyNoInteractions(clients, processos);
    }

    @Test void recognizedHeaderWarnsAboutUnknownNonEmptyColumnsWithoutFailingValidRows() throws Exception {
        var result = service.importar(area, workbook(new String[]{"Cliente", " Juízo ", "\u00a0 ", "Observação"},
            new String[]{"Ana", "Ignorado", "", "Outro"}), user);
        assertThat(result.avisos()).containsExactly(
            "Aba \"Geral\": coluna \"Juízo\" não reconhecida (ignorada)",
            "Aba \"Geral\": coluna \"Observação\" não reconhecida (ignorada)");
        assertThat(result.erros()).isEmpty(); assertThat(result.processosCriados()).isEqualTo(1);
    }

    @Test void recognizedHeaderWithoutClientWarnsEvenWhenSheetHasNoDataRows() throws Exception {
        var result = service.importar(area, workbook(new String[]{"Número do processo", "Juízo"}), user);
        assertThat(result.avisos()).containsExactly(
            "Aba \"Geral\": coluna \"Juízo\" não reconhecida (ignorada)",
            "Aba \"Geral\": coluna \"Cliente\" ausente no cabeçalho");
        assertThat(result.processosCriados()).isZero();
        verifyNoInteractions(clients, processos);
    }

    @Test void sizeLimitUsesStringLengthIncludingSurrogatePairs() throws Exception {
        var result = service.importar(area, workbook(header, new String[]{"\uD83D\uDE00".repeat(26), "Ana"}), user);
        assertThat(result.erros()).singleElement().asString().contains("Número do processo excede 50 caracteres");
        assertThat(result.processosCriados()).isZero();
        verifyNoInteractions(clients, processos);
    }
}
