package com.jurisflow.modules.mcp.server.tools;
import com.fasterxml.jackson.databind.JsonNode;
import com.jurisflow.modules.cliente.ClienteService;
import com.jurisflow.modules.mcp.server.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;
@Component
@RequiredArgsConstructor
public class BuscarClientesTool implements McpTool {
    private final ClienteService clientes;
    public String name() { return "buscar_clientes"; }
    public String description() { return "Busca clientes pelo nome no escritório do token, com paginação de até 50 por página. Clientes não são restritos por área."; }
    public JsonNode inputSchema() { return McpArguments.schema("busca:string", "pagina:integer", "tamanho:integer"); }
    public boolean write() { return false; }
    public java.util.Map<String, Object> annotations() { return java.util.Map.of("title", "Buscar Clientes", "readOnlyHint", true); }
    public Object call(JsonNode args, McpCallContext ctx) {
        var page = PageRequest.of(McpArguments.integer(args, "pagina", 0), McpArguments.integer(args, "tamanho", 20));
        var result = clientes.list(McpArguments.text(args, "busca"), page);
        return java.util.Map.of("clientes", result.getContent(), "pagina", result.getNumber(), "tamanho", result.getSize(),
            "totalElementos", result.getTotalElements(), "totalPaginas", result.getTotalPages());
    }
}
