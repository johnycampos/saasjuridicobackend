package com.jurisflow.modules.mcp.server.tools;
import com.fasterxml.jackson.databind.JsonNode;
import com.jurisflow.modules.processo.ProcessoService;
import com.jurisflow.modules.processo.dto.ProcessoRequest;
import com.jurisflow.modules.group.GroupService;
import com.jurisflow.modules.board.*;
import com.jurisflow.modules.cliente.*;
import com.jurisflow.modules.cliente.dto.ClienteRequest;
import com.jurisflow.modules.mcp.server.*;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import static com.jurisflow.modules.mcp.server.McpArguments.*;
@Component
@RequiredArgsConstructor
public class CriarProcessoTool implements McpTool {
    private final ProcessoService processos;
    private final GroupService groups;
    private final BoardColumnRepository columns;
    private final ClienteService clientes;
    private final ClienteRepository clienteRepository;
    public String name() { return "criar_processo"; }
    public String description() { return "Cria um processo com cliente existente ou por nome. Área obrigatória quando há múltiplas áreas; coluna padrão é a primeira. Exige MEMBER ou superior."; }
    public JsonNode inputSchema() {
        var result = (com.fasterxml.jackson.databind.node.ObjectNode) schema("clienteId:uuid", "clienteNome:string", "numeroProcesso:string", "descricao:string", "tipoAcao:string",
            "vara:string", "comarca:string", "estado:string", "tribunal:string", "reu:string", "valorCausa:number",
            "dataDistribuicao:date", "areaId:uuid", "colunaId:uuid");
        var alternatives = result.putArray("oneOf");
        alternatives.addObject().putArray("required").add("clienteId");
        alternatives.addObject().putArray("required").add("clienteNome");
        return result;
    }
    public boolean write() { return true; }
    public Map<String, Object> annotations() { return Map.of("title", "Criar Processo", "readOnlyHint", false, "destructiveHint", false); }
    @Transactional
    public Object call(JsonNode args, McpCallContext ctx) {
        UUID area = uuid(args, "areaId");
        var restriction = groups.resolveGroupRestriction(ctx.tenantId(), ctx.userId());
        if (restriction.isPresent()) {
            Set<UUID> allowed = restriction.get();
            if (area == null && allowed.size() == 1) area = allowed.iterator().next();
            if (area == null) throw new BusinessException("Informe uma área autorizada para criar o processo");
            if (!allowed.contains(area)) throw BusinessException.forbidden();
        }
        if (area == null) throw new BusinessException("Informe a área para criar o processo");
        groups.assertGroupInTenant(area, ctx.tenantId());
        UUID columnId = uuid(args, "colunaId");
        BoardColumn column;
        if (columnId == null) {
            column = columns.findByGroupIdOrderByPosicaoAsc(area).stream().findFirst()
                .orElseThrow(() -> new BusinessException("A área não possui coluna"));
        } else {
            column = columns.findById(columnId).orElseThrow(() -> BusinessException.notFound("Coluna"));
        }
        if (!ctx.tenantId().equals(column.getTenantId()) || !area.equals(column.getGroupId())) throw BusinessException.forbidden();
        UUID clienteId = uuid(args, "clienteId");
        String nome = text(args, "clienteNome");
        if ((clienteId == null) == (nome == null)) throw new BusinessException("Informe clienteId ou clienteNome, exclusivamente");
        if (clienteId != null) clientes.getById(clienteId);
        else {
            String normalized = nome.trim();
            clienteId = clienteRepository.findByTenantIdAndNomeIgnoreCase(ctx.tenantId(), normalized).map(Cliente::getId)
                .orElseGet(() -> clientes.create(new ClienteRequest(normalized, null, null, null, null, null, null)).id());
        }
        return processos.create(new ProcessoRequest(clienteId, text(args, "descricao"), text(args, "numeroProcesso"),
            text(args, "tipoAcao"), text(args, "vara"), text(args, "comarca"), text(args, "estado"), text(args, "tribunal"),
            text(args, "reu"), args.has("valorCausa") ? args.get("valorCausa").decimalValue() : null,
            args.has("dataDistribuicao") ? LocalDate.parse(text(args, "dataDistribuicao")) : null, area, column.getId()), ctx.principal());
    }
}
