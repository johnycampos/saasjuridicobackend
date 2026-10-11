package com.jurisflow.modules.mcp.server.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.jurisflow.modules.financeiro.ContratoService;
import com.jurisflow.modules.mcp.server.McpArguments;
import com.jurisflow.modules.mcp.server.McpCallContext;
import com.jurisflow.modules.mcp.server.McpTool;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ConsultarFinanceiroProcessoTool implements McpTool {
    private final ContratoService contratos;

    public String name() { return "consultar_financeiro_processo"; }
    public String description() { return "Consulta o financeiro (contrato, parcelas e saldo restante) de um processo autorizado."; }
    public JsonNode inputSchema() { return McpArguments.schema("processoId:uuid:required"); }
    public boolean write() { return false; }
    public Map<String, Object> annotations() { return Map.of("title", "Consultar Financeiro do Processo", "readOnlyHint", true); }

    public Object call(JsonNode args, McpCallContext ctx) {
        var id = McpArguments.uuid(args, "processoId");
        try {
            var contrato = contratos.getByProcesso(id, ctx.userId());
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("contrato", contrato);
            res.put("parcelas", contrato.parcelas());
            res.put("saldoRestante", contrato.saldoRestante());
            return res;
        } catch (BusinessException e) {
            if (e.getMessage() != null && e.getMessage().contains("Contrato")) {
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("contrato", null);
                res.put("parcelas", List.of());
                res.put("saldoRestante", BigDecimal.ZERO);
                return res;
            }
            throw e;
        }
    }
}
