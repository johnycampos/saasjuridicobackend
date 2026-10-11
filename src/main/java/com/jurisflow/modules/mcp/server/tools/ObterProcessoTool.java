package com.jurisflow.modules.mcp.server.tools;
import com.fasterxml.jackson.databind.JsonNode;
import com.jurisflow.modules.audiencia.AudienciaService;
import com.jurisflow.modules.processo.ProcessoService;
import com.jurisflow.modules.tarefa.TarefaService;
import com.jurisflow.modules.movimento.MovimentoService;
import com.jurisflow.modules.processolink.ProcessoLinkService;
import com.jurisflow.modules.mcp.server.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Map;
@Component
@RequiredArgsConstructor
public class ObterProcessoTool implements McpTool {
    private final ProcessoService processos;
    private final TarefaService tarefas;
    private final ProcessoLinkService links;
    private final MovimentoService movimentos;
    private final AudienciaService audiencias;
    public String name() { return "obter_processo"; }
    public String description() { return "Obtém um processo autorizado, suas tarefas, links, audiências e últimas 20 movimentações."; }
    public JsonNode inputSchema() { return McpArguments.schema("processoId:uuid:required"); }
    public boolean write() { return false; }
    public Map<String, Object> annotations() { return Map.of("title", "Obter Processo", "readOnlyHint", true); }
    public Object call(JsonNode args, McpCallContext ctx) {
        var id = McpArguments.uuid(args, "processoId");
        var processo = processos.getById(id, ctx.userId());
        return Map.of(
            "processo", processo,
            "tarefas", tarefas.listByProcesso(id),
            "links", links.listByProcesso(id),
            "movimentacoes", movimentos.listByProcesso(id, 20),
            "audiencias", audiencias.listByProcesso(id, ctx.tenantId())
        );
    }
}
