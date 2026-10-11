package com.jurisflow.modules.mcp.server.tools;
import com.fasterxml.jackson.databind.JsonNode;
import com.jurisflow.modules.processo.*;
import com.jurisflow.modules.mcp.McpTarefaWriteService;
import com.jurisflow.modules.tarefa.dto.TarefaRequest;
import com.jurisflow.modules.mcp.server.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import static com.jurisflow.modules.mcp.server.McpArguments.*;
@Component
@RequiredArgsConstructor
public class CriarTarefaTool implements McpTool {
    private final McpTarefaWriteService tarefas;
    public String name() { return "criar_tarefa"; }
    public String description() { return "Cria uma tarefa em processo autorizado. Exige papel MEMBER ou superior."; }
    public JsonNode inputSchema() { return schema("processoId:uuid:required", "titulo:string:required", "descricao:string", "prioridade:priority", "prazo:date", "horaPrazo:time"); }
    public boolean write() { return true; }
    public java.util.Map<String, Object> annotations() { return java.util.Map.of("title", "Criar Tarefa", "readOnlyHint", false, "destructiveHint", false); }
    public Object call(JsonNode args, McpCallContext ctx) {
        var id = uuid(args, "processoId");
        return tarefas.create(id, new TarefaRequest(text(args, "titulo"), text(args, "descricao"),
            args.has("prioridade") ? PrioridadeTipo.valueOf(text(args, "prioridade")) : null,
            args.has("prazo") ? LocalDate.parse(text(args, "prazo")) : null,
            args.has("horaPrazo") ? LocalTime.parse(text(args, "horaPrazo")) : null), ctx);
    }
}
