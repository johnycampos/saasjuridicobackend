package com.jurisflow.modules.mcp.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jurisflow.modules.mcp.oauth.controller.*;
import com.jurisflow.modules.mcp.oauth.dto.*;
import com.jurisflow.modules.mcp.oauth.service.OAuthService;
import com.jurisflow.security.TenantContext;
import com.jurisflow.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OAuthEndpointsTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private OAuthService oAuthService;
    private MockMvc mvc;

    private final UUID tenantId = UUID.randomUUID();
    private final UserPrincipal user = new UserPrincipal(UUID.randomUUID(), "test@user.com", "User", null);

    @BeforeEach
    void setup() {
        oAuthService = mock(OAuthService.class);
        when(oAuthService.getPublicBackendUrl()).thenReturn("http://localhost:8080");

        var wellKnownController = new OAuthWellKnownController(oAuthService);
        var protocolController = new OAuthProtocolController(oAuthService);
        var consentController = new OAuthConsentController(oAuthService);
        var connectionsController = new OAuthConnectionsController(oAuthService);

        mvc = MockMvcBuilders.standaloneSetup(wellKnownController, protocolController, consentController, connectionsController).build();
        TenantContext.setCurrentTenantId(tenantId);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void wellKnownEndpointsReturnValidMetadata() throws Exception {
        mvc.perform(get("/.well-known/oauth-protected-resource"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value("http://localhost:8080/mcp"))
                .andExpect(jsonPath("$.authorization_servers[0]").value("http://localhost:8080"))
                .andExpect(jsonPath("$.scopes_supported[0]").value("mcp"));

        mvc.perform(get("/.well-known/oauth-protected-resource/mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value("http://localhost:8080/mcp"));

        mvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost:8080"))
                .andExpect(jsonPath("$.authorization_endpoint").value("http://localhost:8080/oauth/authorize"))
                .andExpect(jsonPath("$.token_endpoint").value("http://localhost:8080/oauth/token"))
                .andExpect(jsonPath("$.registration_endpoint").value("http://localhost:8080/oauth/register"))
                .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"));

        mvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost:8080"));
    }

    @Test
    void registerClientPostReturnsCreated() throws Exception {
        DcrResponse res = new DcrResponse("client1", null, Instant.now().getEpochSecond(), 0L, "App",
                List.of("https://app.com/cb"), List.of("authorization_code"), List.of("code"), "none");
        when(oAuthService.registerClient(any(), any())).thenReturn(res);

        mvc.perform(post("/oauth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_name\":\"App\",\"redirect_uris\":[\"https://app.com/cb\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client_id").value("client1"));
    }

    @Test
    void authorizeGetRedirectsOrReturnsHtml() throws Exception {
        // Redirection case
        when(oAuthService.handleAuthorize(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new OAuthService.AuthorizeResult("http://localhost/oauth/autorizar?req=123", false, null));

        mvc.perform(get("/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", "cid")
                        .param("redirect_uri", "https://cb.com")
                        .param("code_challenge", "chal")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost/oauth/autorizar?req=123"));

        // Direct HTML error case
        when(oAuthService.handleAuthorize(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new OAuthService.AuthorizeResult(null, true, "Cliente desconhecido"));

        mvc.perform(get("/oauth/authorize"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cliente desconhecido")));
    }

    @Test
    void revokeReturns200() throws Exception {
        mvc.perform(post("/oauth/revoke")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", "jfmcp_abc"))
                .andExpect(status().isOk());
        verify(oAuthService).revokeToken("jfmcp_abc");
    }

    @Test
    void connectionsEndpointListsAndRevokes() throws Exception {
        when(oAuthService.listConnections(eq(tenantId), any()))
                .thenReturn(List.of(new OAuthConnectionResponse("cid", "ChatGPT", Instant.now(), Instant.now())));

        mvc.perform(get("/api/mcp/connections").principal(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientId").value("cid"))
                .andExpect(jsonPath("$[0].clientName").value("ChatGPT"));

        mvc.perform(delete("/api/mcp/connections/cid").principal(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user, null)))
                .andExpect(status().isNoContent());

        verify(oAuthService).revokeConnection(eq(tenantId), any(), eq("cid"));
    }
}
