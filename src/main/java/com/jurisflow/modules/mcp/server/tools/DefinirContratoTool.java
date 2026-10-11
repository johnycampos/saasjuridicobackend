package com.jurisflow.modules.mcp.server.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.jurisflow.modules.mcp.McpWriteService;
import com.jurisflow.modules.financeiro.dto.ContratoRequest;
import com.jurisflow.modules.mcp.server.McpArguments;
import com.jurisflow.modules.mcp.server.McpCallContext;
import com.jurisflow.modules.mcp.server.McpTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class DefinirContratoTool implements McpTool {
    private final McpWriteService writeService;

    public String name() { return "definir_contrato"; }
    public String description() { return "Define ou atualiza o contrato de honorários de um processo autorizado. Exige valorTotal > 0 e papel MEMBER ou superior."; }
    public JsonNode inputSchema() { return McpArguments.schema("processoId:uuid:required", "valorTotal:money:required", "observacoes:string"); }
    public boolean write() { return true; }
    public Map<String, Object> annotations() { return Map.of("title", "Definir Contrato", "readOnlyHint", false, "destructiveHint", false); }

    public Object call(JsonNode args, McpCallContext ctx) {
        var id = McpArguments.uuid(args, "processoId");
        var valorTotal = McpArguments.bigDecimal(args, "valorTotal");
        var observacoes = McpArguments.text(args, "observacoes");
        return writeService.definirContrato(id, new ContratoRequest(valorTotal, observacoes), ctx);
    }
}
