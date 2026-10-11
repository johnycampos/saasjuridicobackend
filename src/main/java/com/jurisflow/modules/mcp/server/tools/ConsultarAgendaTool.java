package com.jurisflow.modules.mcp.server.tools;
import com.fasterxml.jackson.databind.JsonNode;
import com.jurisflow.modules.agenda.AgendaService;
import com.jurisflow.modules.mcp.server.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class ConsultarAgendaTool implements McpTool {
    private final AgendaService agenda;
    public String name() { return "consultar_agenda"; }
    public String description() { return "Consulta os eventos e o resumo do mês, respeitando suas áreas. Google Calendar é compartilhado pelo escritório."; }
    public JsonNode inputSchema() { return McpArguments.schema("ano:integer:required", "mes:integer:required"); }
    public boolean write() { return false; }
    public java.util.Map<String, Object> annotations() { return java.util.Map.of("title", "Consultar Agenda", "readOnlyHint", true); }
    public Object call(JsonNode args, McpCallContext ctx) {
        return agenda.getAgendaCompleta(args.get("ano").intValue(), args.get("mes").intValue(), ctx.principal());
    }
}
