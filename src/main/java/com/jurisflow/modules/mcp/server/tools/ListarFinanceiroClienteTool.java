package com.jurisflow.modules.mcp.server.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.jurisflow.modules.financeiro.ContratoService;
import com.jurisflow.modules.mcp.server.McpArguments;
import com.jurisflow.modules.mcp.server.McpCallContext;
import com.jurisflow.modules.mcp.server.McpTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ListarFinanceiroClienteTool implements McpTool {
    private final ContratoService contratos;

    public String name() { return "listar_financeiro_cliente"; }
    public String description() { return "Lista a situação financeira dos processos autorizados de um cliente."; }
    public JsonNode inputSchema() { return McpArguments.schema("clienteId:uuid:required"); }
    public boolean write() { return false; }
    public Map<String, Object> annotations() { return Map.of("title", "Listar Financeiro do Cliente", "readOnlyHint", true); }

    public Object call(JsonNode args, McpCallContext ctx) {
        var id = McpArguments.uuid(args, "clienteId");
        return Map.of("processos", contratos.listProcessosDoCliente(id, ctx.userId()));
    }
}
