package com.jurisflow.modules.mcp;

import com.fasterxml.jackson.databind.*;
import com.jurisflow.modules.mcp.server.*;
import com.jurisflow.security.*;
import com.jurisflow.shared.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class McpControllerTest {
    ObjectMapper mapper = new ObjectMapper();
    TenantAccessGuard guard = mock(TenantAccessGuard.class);
    McpTool tool = mock(McpTool.class);
    McpController controller;
    MockMvc mvc;
    UserPrincipal user = new UserPrincipal(UUID.randomUUID(), "u@test", "U", null);
    @BeforeEach void setup() {
        when(tool.name()).thenReturn("teste"); when(tool.description()).thenReturn("Teste");
        when(tool.inputSchema()).thenReturn(McpArguments.schema("nome:string:required"));
        controller = new McpController(mapper, List.of(tool), guard);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        TenantContext.setCurrentTenantId(UUID.randomUUID());
    }
    @AfterEach void cleanup() { TenantContext.clear(); }
    @Test void initializeNegotiatesSupportedVersions() throws Exception {
        for (String version : List.of("2025-06-18", "2025-03-26", "2024-11-05", "unknown")) {
            mvc.perform(post("/mcp").contentType("application/json").content("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"%s"}}
                """.formatted(version)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.protocolVersion").value(version.equals("unknown") ? "2025-06-18" : version))
                .andExpect(jsonPath("$.result.serverInfo.name").value("jurisflow"))
                .andExpect(jsonPath("$.result.serverInfo.version").value("1.0.0"))
                .andExpect(jsonPath("$.result.capabilities.tools.listChanged").value(false));
        }
    }
    @Test void listsToolSchemas() throws Exception {
        mvc.perform(post("/mcp").contentType("application/json").content("{\"jsonrpc\":\"2.0\",\"id\":\"abc\",\"method\":\"tools/list\"}"))
            .andExpect(jsonPath("$.id").value("abc")).andExpect(jsonPath("$.result.tools[0].name").value("teste"))
            .andExpect(jsonPath("$.result.tools[0].inputSchema.type").value("object"));
    }
    @Test void unknownMethodIsRpcError() throws Exception {
        mvc.perform(post("/mcp").contentType("application/json").content("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"unknown\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.error.code").value(-32601)).andExpect(jsonPath("$.id").value(7));
    }
    @Test void invalidJsonIsParseError() throws Exception {
        mvc.perform(post("/mcp").contentType("application/json").content("{"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.error.code").value(-32700));
        mvc.perform(post("/mcp").contentType("application/json").content("{\"jsonrpc\":\"2.0\",\"method\":\"ping\"} garbage"))
            .andExpect(jsonPath("$.error.code").value(-32700));
    }
    @Test void malformedEnvelopeIsInvalidRequest() throws Exception {
        mvc.perform(post("/mcp").contentType("application/json").content("[]"))
            .andExpect(jsonPath("$.error.code").value(-32600));
    }
    @Test void initializedNotificationIsAcceptedWithoutBody() throws Exception {
        mvc.perform(post("/mcp").contentType("application/json").content("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
            .andExpect(status().isAccepted()).andExpect(content().string(""));
    }
    @Test void pingAndUnsupportedHttpMethods() throws Exception {
        mvc.perform(post("/mcp").contentType("application/json").content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
            .andExpect(jsonPath("$.result").isMap());
        mvc.perform(get("/mcp")).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/mcp")).andExpect(status().isMethodNotAllowed());
    }
    private JsonNode call(String arguments) {
        var response = controller.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"teste\",\"arguments\":" + arguments + "}}", user);
        return mapper.valueToTree(response.getBody()).path("result");
    }
    @Test void successContentMatchesStructuredContentAndHidesTenant() throws Exception {
        when(tool.call(any(), any())).thenReturn(Map.of("tenantId", "secret", "nested", List.of(Map.of("tenantId", "secret", "id", "p"))));
        JsonNode result = call("{\"nome\":\"ok\"}");
        assertThat(result.has("isError")).isFalse();
        assertThat(result.path("structuredContent").has("tenantId")).isFalse();
        assertThat(result.toString()).doesNotContain("secret");
        assertThat(mapper.readTree(result.path("content").get(0).path("text").asText())).isEqualTo(result.path("structuredContent"));
    }
    @Test void businessFailureIsToolError() {
        when(tool.call(any(), any())).thenThrow(new BusinessException("Acesso negado"));
        assertThat(call("{\"nome\":\"ok\"}").path("isError").asBoolean()).isTrue();
    }
    @Test void unexpectedFailureDoesNotExposeInternals() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(McpController.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            when(tool.call(any(), any())).thenThrow(new RuntimeException("jdbc production password"));
            assertThat(call("{\"nome\":\"ok\"}").toString()).doesNotContain("jdbc", "password").contains("isError");
            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
                assertThat(event.getFormattedMessage()).contains("teste");
                assertThat(event.getThrowableProxy().getMessage()).isEqualTo("jdbc production password");
            });
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
    @Test void rejectsMissingWrongAndUnknownArgumentsBeforeCall() {
        for (String args : List.of("{}", "{\"nome\":1}", "{\"nome\":\"ok\",\"tenantId\":\"foreign\"}"))
            assertThat(call(args).path("isError").asBoolean()).isTrue();
        verify(tool, never()).call(any(), any());
    }
}
