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
import lombok.extern.slf4j.Slf4j;
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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
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
        Map<String, UUID> clientesPorNome = new HashMap<>();
        Set<UUID> clientesCriados = new HashSet<>();
        List<String> erros = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = lerWorkbook(file)) {
            for (Sheet sheet : workbook) {
                String nomeAba = sheet.getSheetName().trim();
                if (nomeAba.isEmpty()) continue;
                Map<String, Integer> campos = mapearCabecalho(sheet.getRow(0), formatter, nomeAba, avisos);

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

                    String numeroProcesso = valor(formatter, row, campos.getOrDefault("numero", -1));
                    String clienteNome = normalizarNome(valor(formatter, row, campos.getOrDefault("cliente", -1)));
                    String tipoAcao = valor(formatter, row, campos.getOrDefault("tipo", -1));
                    String vara = valor(formatter, row, campos.getOrDefault("vara", -1));
                    String comarca = valor(formatter, row, campos.getOrDefault("comarca", -1));
                    String reu = valor(formatter, row, campos.getOrDefault("reu", -1));
                    String descricao = valor(formatter, row, campos.getOrDefault("descricao", -1));

                    boolean linhaVazia = numeroProcesso.isBlank() && clienteNome.isBlank() && tipoAcao.isBlank()
                            && vara.isBlank() && comarca.isBlank() && reu.isBlank() && descricao.isBlank();
                    if (linhaVazia) continue;

                    if (clienteNome.isBlank()) {
                        erros.add("Aba \"" + nomeAba + "\", linha " + (r + 1) + ": Cliente é obrigatório");
                        continue;
                    }

                    int errosAntes = erros.size();
                    validarTamanho(numeroProcesso, "Número do processo", 50, nomeAba, r + 1, erros);
                    validarTamanho(clienteNome, "Cliente", 255, nomeAba, r + 1, erros);
                    validarTamanho(tipoAcao, "Tipo de ação", 255, nomeAba, r + 1, erros);
                    validarTamanho(vara, "Vara", 255, nomeAba, r + 1, erros);
                    validarTamanho(comarca, "Comarca", 255, nomeAba, r + 1, erros);
                    validarTamanho(reu, "Réu", 500, nomeAba, r + 1, erros);
                    if (erros.size() != errosAntes) continue;

                    UUID clienteId = resolverOuCriarCliente(tenantId, clienteNome, clientesPorNome, clientesCriados);

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
        } catch (IOException e) {
            throw new BusinessException("Não foi possível ler o arquivo enviado. Envie uma planilha válida.");
        } catch (Exception e) {
            log.error("Erro ao importar planilha para o grupo {}: {}", groupId, e.getMessage(), e);
            throw new BusinessException("Não foi possível importar a planilha. Tente novamente.");
        }

        return new ImportResultResponse(colunasCriadas, processosCriados, clientesCriados.size(), erros, avisos);
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

    private UUID resolverOuCriarCliente(UUID tenantId, String nomeLimpo, Map<String, UUID> cache, Set<UUID> criados) {
        String chave = nomeLimpo.toLowerCase(Locale.ROOT);
        if (cache.containsKey(chave)) return cache.get(chave);
        UUID id = clienteRepository.findFirstByTenantIdAndNomeIgnoreCaseOrderByCreatedAtAsc(tenantId, nomeLimpo)
                .map(Cliente::getId)
                .orElseGet(() -> {
                    Cliente cliente = new Cliente();
                    cliente.setTenantId(tenantId);
                    cliente.setNome(nomeLimpo);
                    UUID criado = clienteRepository.save(cliente).getId();
                    criados.add(criado);
                    return criado;
                });
        cache.put(chave, id);
        return id;
    }

    private Workbook lerWorkbook(MultipartFile file) throws IOException {
        try (var input = file.getInputStream()) {
            return WorkbookFactory.create(input);
        } catch (org.apache.poi.UnsupportedFileFormatException | org.apache.poi.EmptyFileException
                 | org.apache.poi.EncryptedDocumentException e) {
            throw new BusinessException("Não foi possível ler o arquivo enviado. Envie uma planilha válida.");
        }
    }

    private String normalizarNome(String nome) {
        return nome.replaceAll("[\\s\\u00A0]+", " ").trim();
    }

    private Map<String, Integer> mapearCabecalho(Row header, DataFormatter formatter, String aba, List<String> avisos) {
        Map<String, Integer> campos = new HashMap<>();
        List<String> ignorados = new ArrayList<>();
        if (header != null) for (Cell cell : header) {
            String original = normalizarNome(formatter.formatCellValue(cell));
            String nome = Normalizer.normalize(original, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
            String campo = switch (nome) {
                case "numero processo", "numero do processo", "processo", "nº processo", "n° processo" -> "numero";
                case "cliente" -> "cliente";
                case "tipo de acao", "tipo acao" -> "tipo";
                case "vara", "comarca", "reu", "descricao" -> nome;
                default -> null;
            };
            if (campo != null) campos.putIfAbsent(campo, cell.getColumnIndex());
            else if (!original.isBlank()) ignorados.add(original);
        }
        if (campos.isEmpty()) return Map.of("numero", 0, "cliente", 1, "tipo", 2, "vara", 3, "comarca", 4, "reu", 5, "descricao", 6);
        for (String ignorado : ignorados) avisos.add("Aba \"" + aba + "\": coluna \"" + ignorado + "\" não reconhecida (ignorada)");
        if (!campos.containsKey("cliente")) avisos.add("Aba \"" + aba + "\": coluna \"Cliente\" ausente no cabeçalho");
        return campos;
    }

    private void validarTamanho(String valor, String campo, int limite, String aba, int linha, List<String> erros) {
        if (valor.length() > limite)
            erros.add("Aba \"" + aba + "\", linha " + linha + ": campo " + campo + " excede " + limite + " caracteres");
    }

    private String valor(DataFormatter formatter, Row row, int index) {
        if (index < 0) return "";
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
