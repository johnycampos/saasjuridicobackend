package com.jurisflow.modules.mcp.server.tools;
import com.fasterxml.jackson.databind.JsonNode;
import com.jurisflow.modules.processo.ProcessoService;
import com.jurisflow.modules.tarefa.TarefaService;
import com.jurisflow.modules.mcp.server.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class ListarTarefasProcessoTool implements McpTool {
    private final ProcessoService processos;
    private final TarefaService tarefas;
    public String name() { return "listar_tarefas_processo"; }
    public String description() { return "Lista as tarefas de um processo nas suas áreas autorizadas."; }
    public JsonNode inputSchema() { return McpArguments.schema("processoId:uuid:required"); }
    public boolean write() { return false; }
    public java.util.Map<String, Object> annotations() { return java.util.Map.of("title", "Listar Tarefas do Processo", "readOnlyHint", true); }
    public Object call(JsonNode args, McpCallContext ctx) {
        var id = McpArguments.uuid(args, "processoId");
        processos.getById(id, ctx.userId());
        return java.util.Map.of("tarefas", tarefas.listByProcesso(id));
    }
}
