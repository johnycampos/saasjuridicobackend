package com.jurisflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jurisflow.modules.mcp.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpTokenAuthFilterTest {
    McpTokenService service = mock(McpTokenService.class);
    McpTokenAuthFilter filter = new McpTokenAuthFilter(service, new ObjectMapper(), "http://localhost:8080");
    McpToken token = new McpToken();
    UserPrincipal user = new UserPrincipal(UUID.randomUUID(), "u@test", "U", null);
    String raw = "jfmcp_" + "a".repeat(43);
    @BeforeEach void setup() { token.setId(UUID.randomUUID()); token.setTenantId(UUID.randomUUID()); token.setUserId(user.getId()); }
    @AfterEach void cleanup() { TenantContext.clear(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    private MockHttpServletRequest request(String path, String bearer) {
        var request = new MockHttpServletRequest("POST", path);
        if (bearer != null) request.addHeader("Authorization", "Bearer " + bearer);
        request.addHeader("X-Tenant-ID", UUID.randomUUID().toString());
        return request;
    }
    private void valid() { when(service.authenticate(raw)).thenReturn(Optional.of(token)); when(service.principal(token)).thenReturn(user); }
    private void assertClean() {
        assertThat(TenantContext.getCurrentTenantId()).isNull();
        assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @Test void missingAndJwtBearerReceiveChallenge() throws Exception {
        for (String bearer : Arrays.asList(null, "jwt")) {
            var response = new MockHttpServletResponse();
            filter.doFilter(request("/mcp", bearer), response, (req, res) -> { throw new AssertionError("Must not dispatch"); });
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getHeader("WWW-Authenticate")).contains("Bearer realm=\"jurisflow-mcp\"")
                    .contains("resource_metadata=\"http://localhost:8080/.well-known/oauth-protected-resource/mcp\"");
            assertClean();
        }
    }
    @Test void tokenSetsItsOwnTenantAndUserAndAlwaysCleansUp() throws Exception {
        valid();
        filter.doFilter(request("/mcp", raw), new MockHttpServletResponse(), (req, res) -> {
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo(token.getTenantId());
            assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(user);
        });
        verify(service).touch(token); assertClean();
    }
    @Test void downstreamExceptionStillCleansUp() {
        valid();
        assertThatThrownBy(() -> filter.doFilter(request("/mcp", raw), new MockHttpServletResponse(), (req, res) -> { throw new jakarta.servlet.ServletException("fail"); }))
            .isInstanceOf(jakarta.servlet.ServletException.class);
        assertClean();
    }
    @Test void apiRoutesAreIgnored() throws Exception {
        var response = new MockHttpServletResponse(); boolean[] called = { false };
        filter.doFilter(request("/api/mcp/tokens", raw), response, (req, res) -> called[0] = true);
        assertThat(called[0]).isTrue(); verifyNoInteractions(service);
    }
    @Test void rateLimitIsPerTokenAndReturnsJsonRpc429() throws Exception {
        valid();
        for (int i = 0; i < 120; i++) {
            var response = new MockHttpServletResponse();
            filter.doFilter(request("/mcp", raw), response, (req, res) -> {});
            assertThat(response.getStatus()).isEqualTo(200);
        }
        var response = new MockHttpServletResponse();
        filter.doFilter(request("/mcp", raw), response, (req, res) -> { throw new AssertionError("Rate limited"); });
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(new ObjectMapper().readTree(response.getContentAsByteArray()).path("error").path("code").asInt()).isEqualTo(-32000);
        assertClean();
    }
    @Test void jwtFilterIgnoresMcpAndDoesNotAuthenticatePatOnApi() throws Exception {
        var provider = mock(JwtTokenProvider.class); var jwt = new JwtAuthenticationFilter(provider);
        jwt.doFilter(request("/mcp", raw), new MockHttpServletResponse(), (req, res) -> {});
        verifyNoInteractions(provider);
        jwt.doFilter(request("/api/mcp/tokens", raw), new MockHttpServletResponse(), (req, res) -> {});
        verify(provider).validateToken(raw, "ACCESS"); assertClean();
    }
}
