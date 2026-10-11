package com.jurisflow.modules.mcp.server.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.jurisflow.modules.mcp.McpWriteService;
import com.jurisflow.modules.financeiro.dto.ParcelaRequest;
import com.jurisflow.modules.mcp.server.McpArguments;
import com.jurisflow.modules.mcp.server.McpCallContext;
import com.jurisflow.modules.mcp.server.McpTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdicionarParcelaTool implements McpTool {
    private final McpWriteService writeService;

    public String name() { return "adicionar_parcela"; }
    public String description() { return "Adiciona uma parcela ao contrato do processo autorizado. Exige contrato prévio e papel MEMBER ou superior."; }
    public JsonNode inputSchema() { return McpArguments.schema("processoId:uuid:required", "valor:money:required", "vencimento:date", "numero:integer"); }
    public boolean write() { return true; }
    public Map<String, Object> annotations() { return Map.of("title", "Adicionar Parcela", "readOnlyHint", false, "destructiveHint", false); }

    public Object call(JsonNode args, McpCallContext ctx) {
        var processoId = McpArguments.uuid(args, "processoId");
        var valor = McpArguments.bigDecimal(args, "valor");
        LocalDate vencimento = args.hasNonNull("vencimento") ? LocalDate.parse(args.get("vencimento").asText()) : null;
        Integer numero = args.hasNonNull("numero") ? args.get("numero").asInt() : null;
        return writeService.adicionarParcelaAoProcesso(processoId, new ParcelaRequest(numero, valor, vencimento), ctx);
    }
}
