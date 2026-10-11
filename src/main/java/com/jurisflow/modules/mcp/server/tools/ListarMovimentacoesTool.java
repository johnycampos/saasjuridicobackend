package com.jurisflow.modules.mcp.server.tools;
import com.fasterxml.jackson.databind.JsonNode;
import com.jurisflow.modules.processo.ProcessoService;
import com.jurisflow.modules.movimento.MovimentoService;
import com.jurisflow.modules.mcp.server.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class ListarMovimentacoesTool implements McpTool {
    private final ProcessoService processos;
    private final MovimentoService movimentos;
    public String name() { return "listar_movimentacoes"; }
    public String description() { return "Lista as movimentações mais recentes de um processo autorizado (padrão: 20, máximo: 100)."; }
    public JsonNode inputSchema() { return McpArguments.schema("processoId:uuid:required", "limite:integer"); }
    public boolean write() { return false; }
    public java.util.Map<String, Object> annotations() { return java.util.Map.of("title", "Listar Movimentações", "readOnlyHint", true); }
    public Object call(JsonNode args, McpCallContext ctx) {
        var id = McpArguments.uuid(args, "processoId");
        processos.getById(id, ctx.userId());
        return java.util.Map.of("movimentacoes", movimentos.listByProcesso(id, McpArguments.integer(args, "limite", 20)));
    }
}
