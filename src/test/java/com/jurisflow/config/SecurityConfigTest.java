package com.jurisflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jurisflow.modules.mcp.McpTokenService;
import com.jurisflow.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.startsWith;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {SecurityConfig.class, SecurityConfigTest.Config.class})
@WebAppConfiguration
@TestPropertySource(properties = "app.cors-origins=http://localhost")
class SecurityConfigTest {
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void unauthenticatedApiReturnsUnauthorizedWithoutOAuthRedirect() throws Exception {
        mvc.perform(get("/api/processos")).andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist("Location"));
        mvc.perform(get("/api/processos").accept("text/html")).andExpect(status().isUnauthorized());
    }

    @Test void googleLoginStillRedirectsToGoogle() throws Exception {
        mvc.perform(get("/oauth2/authorization/google")).andExpect(status().isFound())
            .andExpect(header().string("Location", startsWith("https://accounts.google.com/o/oauth2/v2/auth")));
    }

    @Test void googleCallbackFailureStillUsesLoginFlow() throws Exception {
        mvc.perform(get("/login/oauth2/code/google").param("code", "invalid").param("state", "invalid"))
            .andExpect(status().isFound()).andExpect(redirectedUrl("/login?error"));
    }

    @Configuration
    @EnableWebMvc
    static class Config {
        @Bean JwtAuthenticationFilter jwtAuthenticationFilter() { return new JwtAuthenticationFilter(mock(JwtTokenProvider.class)); }
        @Bean ApiKeyAuthFilter apiKeyAuthFilter() { return new ApiKeyAuthFilter(); }
        @Bean McpTokenAuthFilter mcpTokenAuthFilter() { return new McpTokenAuthFilter(mock(McpTokenService.class), new ObjectMapper(), "dev-secret"); }
        @Bean OAuth2SuccessHandler oAuth2SuccessHandler() { return mock(OAuth2SuccessHandler.class); }
        @Bean ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("google")
                .clientId("test-client").clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").scope("openid", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub").clientName("Google").build());
        }
    }
}
