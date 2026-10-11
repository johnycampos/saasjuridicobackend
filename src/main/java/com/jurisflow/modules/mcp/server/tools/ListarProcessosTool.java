package com.jurisflow.modules.mcp.server.tools;
import com.fasterxml.jackson.databind.JsonNode;
import com.jurisflow.modules.processo.ProcessoService;
import com.jurisflow.modules.mcp.server.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;
import static com.jurisflow.modules.mcp.server.McpArguments.*;
@Component
@RequiredArgsConstructor
public class ListarProcessosTool implements McpTool {
    private final ProcessoService processos;
    private final com.jurisflow.modules.group.GroupService groups;
    public String name() { return "listar_processos"; }
    public String description() { return "Lista processos autorizados, com filtro por área, busca e paginação (até 50 por página)."; }
    public JsonNode inputSchema() { return schema("areaId:uuid", "busca:string", "pagina:integer", "tamanho:integer"); }
    public boolean write() { return false; }
    public java.util.Map<String, Object> annotations() { return java.util.Map.of("title", "Listar Processos", "readOnlyHint", true); }
    public Object call(JsonNode args, McpCallContext ctx) {
        var page = PageRequest.of(integer(args, "pagina", 0), integer(args, "tamanho", 20));
        var area = uuid(args, "areaId");
        String busca = text(args, "busca");
        if (area != null) {
            groups.assertGroupInTenant(area, ctx.tenantId());
            if (busca == null || busca.isBlank()) return processos.listByGroup(area, page, ctx.userId());
            return processos.searchByGroup(area, busca, page, ctx.userId());
        }
        return busca != null && !busca.isBlank() ? processos.search(busca, page, ctx.userId()) : processos.listByTenant(page, ctx.userId());
    }
}
