package com.jurisflow.modules.mcp.server.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.jurisflow.modules.mcp.McpWriteService;
import com.jurisflow.modules.mcp.server.McpArguments;
import com.jurisflow.modules.mcp.server.McpCallContext;
import com.jurisflow.modules.mcp.server.McpTool;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ExcluirParcelaTool implements McpTool {
    private final McpWriteService writeService;

    public String name() { return "excluir_parcela"; }
    public String description() { return "Exclui uma parcela pendente de contrato em processo autorizado. Exige confirmar=true e papel MEMBER ou superior."; }
    public JsonNode inputSchema() { return McpArguments.schema("parcelaId:uuid:required", "confirmar:boolean:required"); }
    public boolean write() { return true; }
    public Map<String, Object> annotations() { return Map.of("title", "Excluir Parcela", "readOnlyHint", false, "destructiveHint", true); }

    public Object call(JsonNode args, McpCallContext ctx) {
        if (!args.path("confirmar").asBoolean(false)) {
            throw new BusinessException("Confirme a exclusão enviando confirmar=true");
        }
        var id = McpArguments.uuid(args, "parcelaId");
        writeService.excluirParcela(id, ctx);
        return Map.of("sucesso", true, "mensagem", "Parcela excluída com sucesso");
    }
}
