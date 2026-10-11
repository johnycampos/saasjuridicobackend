package com.jurisflow.security;

import com.jurisflow.modules.auth.AuthController;
import com.jurisflow.modules.user.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class JwtTokenTypeTest {
    private static final String SECRET = "jwt-type-test-secret-at-least-32-bytes-long";
    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60000, 120000);
    private final UUID userId = UUID.randomUUID();
    private final String access = provider.generateAccessToken(userId, "user@test");
    private final String refresh = provider.generateRefreshToken(userId, "user@test");

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void validatesSignatureExpirationAndExpectedType() {
        assertThat(provider.validateToken(access, "ACCESS")).isTrue();
        assertThat(provider.validateToken(refresh, "REFRESH")).isTrue();
        assertThat(provider.validateToken(refresh, "ACCESS")).isFalse();
        assertThat(provider.validateToken(access, "REFRESH")).isFalse();
        assertThat(provider.validateToken("invalid", "ACCESS")).isFalse();
        var expired = new JwtTokenProvider(SECRET, -1000, -1000);
        assertThat(provider.validateToken(expired.generateAccessToken(userId, "user@test"), "ACCESS")).isFalse();
        var otherKey = new JwtTokenProvider(SECRET + "other", 60000, 120000);
        assertThat(provider.validateToken(otherKey.generateRefreshToken(userId, "user@test"), "REFRESH")).isFalse();
    }

    @Test void missingOrUnknownTypeNeverAuthenticates() throws Exception {
        for (String type : Arrays.asList(null, "OTHER")) {
            var builder = Jwts.builder().subject(userId.toString()).claim("email", "user@test");
            if (type != null) builder.claim("type", type);
            String token = builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
            assertThat(provider.validateToken(token, "ACCESS")).isFalse();
            assertThat(provider.validateToken(token, "REFRESH")).isFalse();
            filter(token, false);
        }
    }

    private void filter(String token, boolean expectedAuthentication) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/mcp/tokens");
        request.addHeader("Authorization", "Bearer " + token);
        new JwtAuthenticationFilter(provider).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (expectedAuthentication) {
                assertThat(authentication).isNotNull();
                assertThat(((UserPrincipal) authentication.getPrincipal()).getId()).isEqualTo(userId);
            } else assertThat(authentication).isNull();
        });
    }

    @Test void refreshTokenIsRejectedByFilterOnTokenManagementRoute() throws Exception { filter(refresh, false); }
    @Test void accessTokenStillAuthenticatesOnTokenManagementRoute() throws Exception { filter(access, true); }

    @Test void refreshEndpointRejectsAccessToken() throws Exception {
        var users = mock(UserService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(provider, users)).build();
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
            .content("{\"refreshToken\":\"" + access + "\"}"))
            .andExpect(status().isBadRequest()).andExpect(content().string(""));
        verifyNoInteractions(users);
    }

    @Test void refreshEndpointAcceptsRefreshToken() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(provider, mock(UserService.class))).build();
        var response = mvc.perform(post("/api/auth/refresh").contentType("application/json")
            .content("{\"refreshToken\":\"" + refresh + "\"}"))
            .andExpect(status().isOk()).andReturn().getResponse();
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertThat(provider.validateToken(json.path("accessToken").asText(), "ACCESS")).isTrue();
        assertThat(provider.validateToken(json.path("refreshToken").asText(), "REFRESH")).isTrue();
    }
}
