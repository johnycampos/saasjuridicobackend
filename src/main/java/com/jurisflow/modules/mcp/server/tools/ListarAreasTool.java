package com.jurisflow.modules.mcp.server.tools;
import com.fasterxml.jackson.databind.JsonNode;
import com.jurisflow.modules.group.GroupService;
import com.jurisflow.modules.mcp.server.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.Pageable;
@Component
@RequiredArgsConstructor
public class ListarAreasTool implements McpTool {
    private final GroupService groups;
    public String name() { return "listar_areas"; }
    public String description() { return "Lista as áreas do escritório que você pode acessar."; }
    public JsonNode inputSchema() { return McpArguments.schema(); }
    public boolean write() { return false; }
    public java.util.Map<String, Object> annotations() { return java.util.Map.of("title", "Listar Áreas", "readOnlyHint", true); }
    public Object call(JsonNode args, McpCallContext ctx) { return java.util.Map.of("areas", groups.listByTenant(Pageable.unpaged(), ctx.userId()).getContent()); }
}
