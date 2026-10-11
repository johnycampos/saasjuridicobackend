package com.jurisflow.modules.mcp.server.tools;
import com.fasterxml.jackson.databind.JsonNode;
import com.jurisflow.modules.mcp.McpTarefaWriteService;
import com.jurisflow.modules.mcp.server.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class ConcluirTarefaTool implements McpTool {
    private final McpTarefaWriteService tarefas;
    public String name() { return "concluir_tarefa"; }
    public String description() { return "Conclui ou reabre tarefa em processo autorizado. Exige papel MEMBER ou superior."; }
    public JsonNode inputSchema() { return McpArguments.schema("tarefaId:uuid:required", "concluida:boolean"); }
    public boolean write() { return true; }
    public java.util.Map<String, Object> annotations() { return java.util.Map.of("title", "Concluir Tarefa", "readOnlyHint", false, "destructiveHint", false); }
    public Object call(JsonNode args, McpCallContext ctx) {
        var id = McpArguments.uuid(args, "tarefaId");
        return tarefas.toggleConcluida(id, args.path("concluida").asBoolean(true), ctx);
    }
}
