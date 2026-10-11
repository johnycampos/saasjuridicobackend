package com.jurisflow.modules.mcp.server;
import com.fasterxml.jackson.databind.JsonNode;
public interface McpTool {
    String name();
    String description();
    JsonNode inputSchema();
    boolean write();
    Object call(JsonNode args, McpCallContext ctx);
    default java.util.Map<String, Object> annotations() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (!write()) {
            m.put("readOnlyHint", true);
        } else {
            m.put("readOnlyHint", false);
            m.put("destructiveHint", false);
        }
        return m;
    }
}
