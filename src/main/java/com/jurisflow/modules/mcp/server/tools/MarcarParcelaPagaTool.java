package com.jurisflow.modules.mcp.server.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.jurisflow.modules.mcp.McpWriteService;
import com.jurisflow.modules.mcp.server.McpArguments;
import com.jurisflow.modules.mcp.server.McpCallContext;
import com.jurisflow.modules.mcp.server.McpTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MarcarParcelaPagaTool implements McpTool {
    private final McpWriteService writeService;

    public String name() { return "marcar_parcela_paga"; }
    public String description() { return "Marca uma parcela de honorários como paga. Exige papel MEMBER ou superior."; }
    public JsonNode inputSchema() { return McpArguments.schema("parcelaId:uuid:required"); }
    public boolean write() { return true; }
    public Map<String, Object> annotations() { return Map.of("title", "Marcar Parcela como Paga", "readOnlyHint", false, "destructiveHint", false); }

    public Object call(JsonNode args, McpCallContext ctx) {
        var parcelaId = McpArguments.uuid(args, "parcelaId");
        return writeService.marcarParcelaPaga(parcelaId, ctx);
    }
}
