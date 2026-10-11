package com.jurisflow.modules.mcp.server.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jurisflow.modules.mcp.McpWriteService;
import com.jurisflow.modules.mcp.server.*;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import static com.jurisflow.modules.mcp.server.McpArguments.*;

@Component
@RequiredArgsConstructor
public class AdicionarMovimentacaoTool implements McpTool {
    private final McpWriteService movimentos;
    public String name() { return "adicionar_movimentacao"; }
    public String description() {
        return "Registra movimentação manual, como decisão recebida por e-mail ainda não sincronizada. "
            + "Exige MEMBER ou superior. dataHora: data e hora local sem fuso (yyyy-MM-dd'T'HH:mm:ss), "
            + "no mesmo padrão das movimentações exibidas. nome: título; texto: conteúdo opcional. "
            + "Consulte listar_movimentacoes antes para evitar duplicação. Uma sincronização posterior "
            + "não reconcilia automaticamente o registro manual.";
    }
    public JsonNode inputSchema() {
        ObjectNode result = (ObjectNode) schema("processoId:uuid:required", "nome:string:required",
            "dataHora:string:required", "texto:string");
        ObjectNode properties = (ObjectNode) result.get("properties");
        ((ObjectNode) properties.get("nome")).put("minLength", 1).put("maxLength", 500);
        ((ObjectNode) properties.get("texto")).put("maxLength", 100000);
        ((ObjectNode) properties.get("dataHora")).put("pattern",
            "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}$");
        return result;
    }
    public boolean write() { return true; }
    public Map<String, Object> annotations() {
        return Map.of("title", "Adicionar Movimentação Manual", "readOnlyHint", false, "destructiveHint", false);
    }
    public Object call(JsonNode args, McpCallContext ctx) {
        LocalDateTime dataHora;
        try { dataHora = LocalDateTime.parse(text(args, "dataHora")); }
        catch (DateTimeParseException e) {
            throw new BusinessException("dataHora inválida. Use yyyy-MM-dd'T'HH:mm:ss sem fuso.");
        }
        return movimentos.adicionarMovimentacao(uuid(args, "processoId"), text(args, "nome"),
            dataHora, text(args, "texto"), ctx);
    }
}
