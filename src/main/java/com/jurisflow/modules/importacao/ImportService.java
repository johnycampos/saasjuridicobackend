package com.jurisflow.modules.importacao;

import com.jurisflow.modules.board.BoardColumn;
import com.jurisflow.modules.board.BoardColumnRepository;
import com.jurisflow.modules.cliente.Cliente;
import com.jurisflow.modules.cliente.ClienteRepository;
import com.jurisflow.modules.group.GroupRepository;
import com.jurisflow.modules.group.GroupService;
import com.jurisflow.modules.importacao.dto.ImportResultResponse;
import com.jurisflow.modules.processo.ProcessoService;
import com.jurisflow.modules.processo.dto.ProcessoRequest;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImportService {

    private static final List<String> COLUNAS_MODELO = List.of(
            "Numero processo", "Cliente", "Tipo de Ação", "Vara", "Comarca", "Réu", "Descrição"
    );

    private final BoardColumnRepository boardColumnRepository;
    private final GroupRepository groupRepository;
    private final GroupService groupService;
    private final ClienteRepository clienteRepository;
    private final ProcessoService processoService;

    public byte[] gerarPlanilhaModelo(UUID groupId, UUID userId) {
        requireAcesso(groupId, userId);

        List<BoardColumn> colunas = boardColumnRepository.findByGroupIdOrderByPosicaoAsc(groupId);
        if (colunas.isEmpty()) {
            BoardColumn fallback = new BoardColumn();
            fallback.setNome("Geral");
            colunas = List.of(fallback);
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Set<String> nomesUsados = new HashSet<>();
            for (BoardColumn coluna : colunas) {
                String nomeAba = nomeAbaUnico(coluna.getNome(), nomesUsados);
                Sheet sheet = workbook.createSheet(nomeAba);
                Row header = sheet.createRow(0);
                for (int i = 0; i < COLUNAS_MODELO.size(); i++) {
                    Cell cell = header.createCell(i);
                    cell.setCellValue(COLUNAS_MODELO.get(i));
                    cell.setCellStyle(headerStyle);
                    sheet.setColumnWidth(i, 22 * 256);
                }
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("Erro ao gerar planilha modelo");
        }
    }

    @Transactional
    public ImportResultResponse importar(UUID groupId, MultipartFile file, UserPrincipal principal) {
        requireAcesso(groupId, principal.getId());
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Envie um arquivo de planilha");
        }

        UUID tenantId = TenantContext.getCurrentTenantId();
        Map<String, BoardColumn> colunasPorNome = new HashMap<>();
        for (BoardColumn coluna : boardColumnRepository.findByGroupIdOrderByPosicaoAsc(groupId)) {
            colunasPorNome.put(coluna.getNome().trim().toLowerCase(), coluna);
        }

        int colunasCriadas = 0;
        int processosCriados = 0;
        List<String> erros = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            for (Sheet sheet : workbook) {
                String nomeAba = sheet.getSheetName().trim();
                if (nomeAba.isEmpty()) continue;

                BoardColumn coluna = colunasPorNome.get(nomeAba.toLowerCase());
                if (coluna == null) {
                    coluna = new BoardColumn();
                    coluna.setTenantId(tenantId);
                    coluna.setGroupId(groupId);
                    coluna.setNome(nomeAba);
                    Integer maxPos = boardColumnRepository.findMaxPosicaoByGroupId(groupId);
                    coluna.setPosicao(maxPos != null ? maxPos + 1 : 0);
                    coluna = boardColumnRepository.save(coluna);
                    colunasPorNome.put(nomeAba.toLowerCase(), coluna);
                    colunasCriadas++;
                }

                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;

                    String numeroProcesso = valor(formatter, row, 0);
                    String clienteNome = valor(formatter, row, 1);
                    String tipoAcao = valor(formatter, row, 2);
                    String vara = valor(formatter, row, 3);
                    String comarca = valor(formatter, row, 4);
                    String reu = valor(formatter, row, 5);
                    String descricao = valor(formatter, row, 6);

                    boolean linhaVazia = numeroProcesso.isBlank() && clienteNome.isBlank() && tipoAcao.isBlank()
                            && vara.isBlank() && comarca.isBlank() && reu.isBlank() && descricao.isBlank();
                    if (linhaVazia) continue;

                    if (clienteNome.isBlank()) {
                        erros.add("Aba \"" + nomeAba + "\", linha " + (r + 1) + ": Cliente é obrigatório");
                        continue;
                    }

                    UUID clienteId = resolverOuCriarCliente(tenantId, clienteNome);

                    ProcessoRequest request = new ProcessoRequest(
                            clienteId, blankToNull(descricao), blankToNull(numeroProcesso), blankToNull(tipoAcao),
                            blankToNull(vara), blankToNull(comarca), null, null, blankToNull(reu),
                            null, null, groupId, coluna.getId()
                    );
                    processoService.create(request, principal);
                    processosCriados++;
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Nao foi possivel ler o arquivo enviado. Envie uma planilha .xlsx valida.");
        }

        return new ImportResultResponse(colunasCriadas, processosCriados, erros);
    }

    private void requireAcesso(UUID groupId, UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        groupRepository.findByIdAndTenantId(groupId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Grupo"));

        var restriction = groupService.resolveGroupRestriction(tenantId, userId);
        if (restriction.isPresent() && !restriction.get().contains(groupId)) {
            throw BusinessException.forbidden();
        }
    }

    private UUID resolverOuCriarCliente(UUID tenantId, String nome) {
        String nomeLimpo = nome.trim();
        return clienteRepository.findByTenantIdAndNomeIgnoreCase(tenantId, nomeLimpo)
                .map(Cliente::getId)
                .orElseGet(() -> {
                    Cliente cliente = new Cliente();
                    cliente.setTenantId(tenantId);
                    cliente.setNome(nomeLimpo);
                    return clienteRepository.save(cliente).getId();
                });
    }

    private String valor(DataFormatter formatter, Row row, int index) {
        Cell cell = row.getCell(index);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private String nomeAbaUnico(String nomeOriginal, Set<String> nomesUsados) {
        String base = WorkbookUtil.createSafeSheetName(nomeOriginal);
        String nome = base;
        int sufixo = 2;
        while (!nomesUsados.add(nome)) {
            String baseCortada = base.length() > 28 ? base.substring(0, 28) : base;
            nome = baseCortada + "-" + sufixo++;
        }
        return nome;
    }
}
