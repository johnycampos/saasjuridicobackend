package com.jurisflow.modules.mcp.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jurisflow.modules.tenant.TenantRole;
import com.jurisflow.security.*;
import com.jurisflow.shared.exception.BusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@lombok.extern.slf4j.Slf4j
@RestController
@RequestMapping("/mcp")
public class McpController {
    private final ObjectMapper mapper;
    private final List<McpTool> tools;
    private final TenantAccessGuard guard;
    public McpController(ObjectMapper mapper, List<McpTool> tools, TenantAccessGuard guard) {
        this.mapper = mapper; this.tools = tools; this.guard = guard;
    }

    @PostMapping(produces = "application/json")
    public ResponseEntity<?> handle(@RequestBody(required = false) String body, @AuthenticationPrincipal UserPrincipal user) {
        JsonNode request;
        try { request = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body == null ? "" : body); }
        catch (JsonProcessingException e) { return rpcError(null, -32700, "JSON inválido"); }
        if (request == null || request.isMissingNode()) return rpcError(null, -32700, "JSON inválido");
        JsonNode id = request.get("id");
        if (!request.isObject() || !"2.0".equals(request.path("jsonrpc").asText()) || !request.path("method").isTextual()
            || (id != null && !id.isNull() && !id.isTextual() && !id.isNumber())
            || (request.has("params") && !request.get("params").isObject()))
            return rpcError(null, -32600, "Requisição inválida");
        String method = request.get("method").asText();
        if ("notifications/initialized".equals(method) && id == null) return ResponseEntity.accepted().build();
        JsonNode params = request.path("params");
        Object result;
        switch (method) {
            case "initialize" -> {
                String version = params.path("protocolVersion").asText();
                if (!Set.of("2025-06-18", "2025-03-26", "2024-11-05").contains(version)) version = "2025-06-18";
                result = Map.of("protocolVersion", version, "capabilities", Map.of("tools", Map.of("listChanged", false)),
                    "serverInfo", Map.of("name", "jurisflow", "version", "1.0.0"),
                    "instructions", "Consulte e crie processos e tarefas nas áreas autorizadas do seu escritório. Escritas exigem papel MEMBER ou superior.");
            }
            case "ping" -> result = Map.of();
            case "tools/list" -> result = Map.of("tools", tools.stream().map(t -> {
                Map<String, Object> toolMap = new LinkedHashMap<>();
                toolMap.put("name", t.name());
                toolMap.put("description", t.description());
                toolMap.put("inputSchema", t.inputSchema());
                var ann = t.annotations();
                toolMap.put("annotations", ann != null ? ann : Map.of());
                return toolMap;
            }).toList());
            case "tools/call" -> result = call(params, user);
            default -> { return rpcError(id, -32601, "Método desconhecido"); }
        }
        if (id == null) return ResponseEntity.accepted().build();
        return ResponseEntity.ok(envelope(id, "result", result));
    }

    private Object call(JsonNode params, UserPrincipal user) {
        try {
            McpTool tool = tools.stream().filter(t -> t.name().equals(params.path("name").asText())).findFirst()
                .orElseThrow(() -> new BusinessException("Tool desconhecida"));
            if (user == null || TenantContext.getCurrentTenantId() == null) throw BusinessException.forbidden();
            McpCallContext ctx = new McpCallContext(TenantContext.getCurrentTenantId(), user);
            guard.requireActiveMember(ctx.tenantId(), ctx.userId());
            if (tool.write()) {
                try { guard.requireMinRole(ctx.tenantId(), ctx.userId(), TenantRole.MEMBER); }
                catch (BusinessException e) { return toolError("Seu papel permite apenas leitura"); }
            }
            JsonNode args = params.has("arguments") ? params.get("arguments") : mapper.createObjectNode();
            McpArguments.validate(args, tool.inputSchema());
            JsonNode data = mapper.valueToTree(tool.call(args, ctx));
            stripTenant(data);
            return Map.of("content", List.of(Map.of("type", "text", "text", mapper.writeValueAsString(data))),
                "structuredContent", data);
        } catch (BusinessException e) { return toolError(e.getMessage()); }
        catch (IllegalArgumentException e) { return toolError("Argumentos inválidos. Confira o schema da tool."); }
        catch (Exception e) {
            log.error("Erro inesperado ao executar tool MCP {}", params.path("name").asText(), e);
            return toolError("Não foi possível executar a operação");
        }
    }

    private void stripTenant(JsonNode node) {
        if (node.isObject()) ((ObjectNode) node).remove("tenantId");
        if (node.isContainerNode()) node.forEach(this::stripTenant);
    }
    private Object toolError(String message) {
        return Map.of("content", List.of(Map.of("type", "text", "text", message)), "isError", true);
    }
    private Map<String, Object> envelope(JsonNode id, String key, Object value) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0"); response.put("id", id); response.put(key, value);
        return response;
    }
    private ResponseEntity<?> rpcError(JsonNode id, int code, String message) {
        return ResponseEntity.ok(envelope(id, "error", Map.of("code", code, "message", message)));
    }
}
