package com.jurisflow.modules.mcp.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jurisflow.modules.mcp.McpToken;
import com.jurisflow.modules.mcp.McpTokenRepository;
import com.jurisflow.modules.mcp.oauth.dto.DcrRequest;
import com.jurisflow.modules.mcp.oauth.dto.DcrResponse;
import com.jurisflow.modules.mcp.oauth.dto.OAuthTokenResponse;
import com.jurisflow.modules.mcp.oauth.entity.OAuthAuthorizationCode;
import com.jurisflow.modules.mcp.oauth.entity.OAuthAuthorizationRequest;
import com.jurisflow.modules.mcp.oauth.entity.OAuthClient;
import com.jurisflow.modules.mcp.oauth.entity.OAuthRefreshToken;
import com.jurisflow.modules.mcp.oauth.exception.OAuthException;
import com.jurisflow.modules.mcp.oauth.repository.OAuthAuthorizationCodeRepository;
import com.jurisflow.modules.mcp.oauth.repository.OAuthAuthorizationRequestRepository;
import com.jurisflow.modules.mcp.oauth.repository.OAuthClientRepository;
import com.jurisflow.modules.mcp.oauth.repository.OAuthRefreshTokenRepository;
import com.jurisflow.modules.mcp.oauth.service.OAuthService;
import com.jurisflow.modules.tenant.Tenant;
import com.jurisflow.modules.tenant.TenantMember;
import com.jurisflow.modules.tenant.TenantMemberRepository;
import com.jurisflow.modules.tenant.TenantRole;
import com.jurisflow.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthServiceTest {

    @Mock private OAuthClientRepository clientRepository;
    @Mock private OAuthAuthorizationRequestRepository authRequestRepository;
    @Mock private OAuthAuthorizationCodeRepository authCodeRepository;
    @Mock private OAuthRefreshTokenRepository refreshTokenRepository;
    @Mock private McpTokenRepository mcpTokenRepository;
    @Mock private TenantMemberRepository tenantMemberRepository;
    @Mock private com.jurisflow.modules.tenant.TenantRepository tenantRepository;

    private final ObjectMapper mapper = new ObjectMapper();
    private OAuthService oAuthService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        oAuthService = new OAuthService(
                clientRepository,
                authRequestRepository,
                authCodeRepository,
                refreshTokenRepository,
                mcpTokenRepository,
                tenantMemberRepository,
                tenantRepository,
                mapper
        );
        ReflectionTestUtils.setField(oAuthService, "publicBackendUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(oAuthService, "frontendUrl", "http://localhost");
    }

    @Test
    void dcrRegistersClientSuccessfully() {
        DcrRequest req = new DcrRequest(
                "ChatGPT JurisFlow",
                List.of("https://chatgpt.com/connector/callback"),
                "none",
                null,
                null
        );

        when(clientRepository.save(any(OAuthClient.class))).thenAnswer(i -> i.getArgument(0));

        DcrResponse res = oAuthService.registerClient(req, "192.168.1.1");
        assertThat(res.client_id()).isNotBlank();
        assertThat(res.client_secret()).isNull();
        assertThat(res.client_name()).isEqualTo("ChatGPT JurisFlow");
        assertThat(res.redirect_uris()).containsExactly("https://chatgpt.com/connector/callback");
    }

    @Test
    void dcrGeneratesSecretForConfidentialClient() {
        DcrRequest req = new DcrRequest(
                "Confidential App",
                List.of("http://localhost:3000/callback"),
                "client_secret_post",
                null,
                null
        );

        when(clientRepository.save(any(OAuthClient.class))).thenAnswer(i -> i.getArgument(0));

        DcrResponse res = oAuthService.registerClient(req, "192.168.1.2");
        assertThat(res.client_secret()).isNotNull();
        assertThat(res.token_endpoint_auth_method()).isEqualTo("client_secret_post");
    }

    @Test
    void dcrRejectsInvalidRedirectUris() {
        // HTTP on remote host is forbidden (must be https or localhost)
        DcrRequest reqHttp = new DcrRequest("Bad App", List.of("http://example.com/callback"), "none", null, null);
        assertThatThrownBy(() -> oAuthService.registerClient(reqHttp, "192.168.1.3"))
                .isInstanceOf(OAuthException.class)
                .hasFieldOrPropertyWithValue("error", "invalid_redirect_uri");

        // Fragment is forbidden
        DcrRequest reqFrag = new DcrRequest("Bad App", List.of("https://example.com/callback#fragment"), "none", null, null);
        assertThatThrownBy(() -> oAuthService.registerClient(reqFrag, "192.168.1.3"))
                .isInstanceOf(OAuthException.class)
                .hasFieldOrPropertyWithValue("error", "invalid_redirect_uri");
    }

    @Test
    void authorizeDirectErrorWhenClientOrRedirectUriMismatch() {
        // Unknown client
        when(clientRepository.findByClientId("unknown")).thenReturn(Optional.empty());
        var res1 = oAuthService.handleAuthorize("code", "unknown", "https://chatgpt.com", "chal", "S256", "st", "mcp", null);
        assertThat(res1.isDirectHtmlError()).isTrue();

        // Redirect URI not registered
        OAuthClient client = new OAuthClient();
        client.setClientId("cid");
        client.setRedirectUris("[\"https://chatgpt.com/callback\"]");
        when(clientRepository.findByClientId("cid")).thenReturn(Optional.of(client));

        var res2 = oAuthService.handleAuthorize("code", "cid", "https://attacker.com/callback", "chal", "S256", "st", "mcp", null);
        assertThat(res2.isDirectHtmlError()).isTrue();
    }

    @Test
    void authorizeRedirectsWithErrorWhenOtherParamsInvalid() {
        OAuthClient client = new OAuthClient();
        client.setClientId("cid");
        client.setRedirectUris("[\"https://chatgpt.com/callback\"]");
        when(clientRepository.findByClientId("cid")).thenReturn(Optional.of(client));

        // Wrong code_challenge_method
        var res = oAuthService.handleAuthorize("code", "cid", "https://chatgpt.com/callback", "chal", "plain", "st123", "mcp", null);
        assertThat(res.isDirectHtmlError()).isFalse();
        assertThat(res.redirectUrl()).contains("https://chatgpt.com/callback?")
                .contains("error=invalid_request")
                .contains("state=st123");
    }

    @Test
    void authorizeSucceedsAndRedirectsToFrontendConsent() {
        OAuthClient client = new OAuthClient();
        client.setClientId("cid");
        client.setRedirectUris("[\"https://chatgpt.com/callback\"]");
        when(clientRepository.findByClientId("cid")).thenReturn(Optional.of(client));

        var res = oAuthService.handleAuthorize("code", "cid", "https://chatgpt.com/callback", "chal", "S256", "st123", "mcp", "http://localhost:8080/mcp");
        assertThat(res.isDirectHtmlError()).isFalse();
        assertThat(res.redirectUrl()).startsWith("http://localhost/oauth/autorizar?req=");

        verify(authRequestRepository).save(any(OAuthAuthorizationRequest.class));
    }

    @Test
    void consentApprovalCreatesAuthorizationCodeAndRedirects() {
        String opaqueReqId = "opaque_123";
        String reqHash = OAuthService.hash(opaqueReqId);

        OAuthAuthorizationRequest req = new OAuthAuthorizationRequest();
        req.setId(UUID.randomUUID());
        req.setRequestHash(reqHash);
        req.setClientId("cid");
        req.setRedirectUri("https://chatgpt.com/callback");
        req.setState("state456");
        req.setCodeChallenge("challenge_abc");
        req.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));

        when(authRequestRepository.findByRequestHash(reqHash)).thenReturn(Optional.of(req));
        when(tenantMemberRepository.existsByTenantIdAndUser_IdAndAtivoTrue(tenantId, userId)).thenReturn(true);
        when(authRequestRepository.consumeRequest(eq(req.getId()), any())).thenReturn(1);

        var redirectRes = oAuthService.approveConsent(opaqueReqId, tenantId, userId);
        assertThat(redirectRes.redirectUrl()).contains("https://chatgpt.com/callback?")
                .contains("code=")
                .contains("state=state456")
                .contains("iss=http%3A%2F%2Flocalhost%3A8080");

        verify(authCodeRepository).save(any(OAuthAuthorizationCode.class));
    }

    @Test
    void consentApprovalFailsIfUserNotActiveMember() {
        String opaqueReqId = "opaque_123";
        String reqHash = OAuthService.hash(opaqueReqId);

        OAuthAuthorizationRequest req = new OAuthAuthorizationRequest();
        req.setId(UUID.randomUUID());
        req.setRequestHash(reqHash);
        req.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));

        when(authRequestRepository.findByRequestHash(reqHash)).thenReturn(Optional.of(req));
        when(tenantMemberRepository.existsByTenantIdAndUser_IdAndAtivoTrue(tenantId, userId)).thenReturn(false);

        assertThatThrownBy(() -> oAuthService.approveConsent(opaqueReqId, tenantId, userId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void tokenExchangeWithValidCodeAndPkceReturnsTokenPair() {
        OAuthClient client = new OAuthClient();
        client.setClientId("cid");
        client.setClientName("Test Client");
        client.setTokenEndpointAuthMethod("none");
        when(clientRepository.findByClientId("cid")).thenReturn(Optional.of(client));

        String codeVerifier = "abcdefghijklmnopqrstuvwxyz0123456789_verifier";
        String codeChallenge = OAuthService.base64UrlSha256(codeVerifier);

        String codeRaw = "code_raw_secret";
        String codeHash = OAuthService.hash(codeRaw);

        OAuthAuthorizationCode authCode = new OAuthAuthorizationCode();
        authCode.setId(UUID.randomUUID());
        authCode.setCodeHash(codeHash);
        authCode.setClientId("cid");
        authCode.setRedirectUri("https://chatgpt.com/callback");
        authCode.setUserId(userId);
        authCode.setTenantId(tenantId);
        authCode.setCodeChallenge(codeChallenge);
        authCode.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));

        when(authCodeRepository.findByCodeHash(codeHash)).thenReturn(Optional.of(authCode));
        when(authCodeRepository.markUsed(eq(authCode.getId()), any(), any())).thenReturn(1);
        when(tenantMemberRepository.existsByTenantIdAndUser_IdAndAtivoTrue(tenantId, userId)).thenReturn(true);

        Map<String, String> params = Map.of(
                "grant_type", "authorization_code",
                "client_id", "cid",
                "code", codeRaw,
                "redirect_uri", "https://chatgpt.com/callback",
                "code_verifier", codeVerifier
        );

        OAuthTokenResponse tokenRes = oAuthService.handleToken(null, "authorization_code", params);
        assertThat(tokenRes.access_token()).startsWith("jfmcp_");
        assertThat(tokenRes.refresh_token()).startsWith("jfrt_");
        assertThat(tokenRes.token_type()).isEqualTo("Bearer");
        assertThat(tokenRes.expires_in()).isEqualTo(3600);

        // Verify McpToken saved with kind=OAUTH
        ArgumentCaptor<McpToken> captor = ArgumentCaptor.forClass(McpToken.class);
        verify(mcpTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo("OAUTH");
        assertThat(captor.getValue().getOauthClientId()).isEqualTo("cid");
        assertThat(captor.getValue().getOauthFamilyId()).isNotNull();
    }

    @Test
    void tokenExchangeFailsWhenPkceVerifierIsWrong() {
        OAuthClient client = new OAuthClient();
        client.setClientId("cid");
        client.setTokenEndpointAuthMethod("none");
        when(clientRepository.findByClientId("cid")).thenReturn(Optional.of(client));

        String codeChallenge = OAuthService.base64UrlSha256("valid_verifier");
        String codeRaw = "code_raw_secret";
        String codeHash = OAuthService.hash(codeRaw);

        OAuthAuthorizationCode authCode = new OAuthAuthorizationCode();
        authCode.setId(UUID.randomUUID());
        authCode.setCodeHash(codeHash);
        authCode.setClientId("cid");
        authCode.setRedirectUri("https://chatgpt.com/callback");
        authCode.setCodeChallenge(codeChallenge);
        authCode.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));

        when(authCodeRepository.findByCodeHash(codeHash)).thenReturn(Optional.of(authCode));

        Map<String, String> params = Map.of(
                "grant_type", "authorization_code",
                "client_id", "cid",
                "code", codeRaw,
                "redirect_uri", "https://chatgpt.com/callback",
                "code_verifier", "WRONG_verifier"
        );

        assertThatThrownBy(() -> oAuthService.handleToken(null, "authorization_code", params))
                .isInstanceOf(OAuthException.class)
                .hasFieldOrPropertyWithValue("error", "invalid_grant");
    }

    @Test
    void refreshTokenRotationAndReuseRevokesFamily() {
        OAuthClient client = new OAuthClient();
        client.setClientId("cid");
        client.setTokenEndpointAuthMethod("none");
        when(clientRepository.findByClientId("cid")).thenReturn(Optional.of(client));

        UUID familyId = UUID.randomUUID();
        String rtRaw = "jfrt_initial_refresh_token";
        String rtHash = OAuthService.hash(rtRaw);

        OAuthRefreshToken rt = new OAuthRefreshToken();
        rt.setId(UUID.randomUUID());
        rt.setTokenHash(rtHash);
        rt.setFamilyId(familyId);
        rt.setClientId("cid");
        rt.setTenantId(tenantId);
        rt.setUserId(userId);
        rt.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));

        // 1. First rotation: success
        when(refreshTokenRepository.findByTokenHash(rtHash)).thenReturn(Optional.of(rt));
        when(refreshTokenRepository.findById(rt.getId())).thenReturn(Optional.of(rt));
        when(refreshTokenRepository.markUsed(eq(rt.getId()), any())).thenReturn(1);
        when(tenantMemberRepository.existsByTenantIdAndUser_IdAndAtivoTrue(tenantId, userId)).thenReturn(true);

        var tokenRes = oAuthService.handleToken(null, "refresh_token", Map.of(
                "grant_type", "refresh_token",
                "client_id", "cid",
                "refresh_token", rtRaw
        ));
        assertThat(tokenRes.refresh_token()).startsWith("jfrt_");
        verify(refreshTokenRepository).lockFamily(familyId);

        // 2. Reuse of already used token: detects reuse and revokes entire family
        rt.setUsedAt(Instant.now());
        assertThatThrownBy(() -> oAuthService.handleToken(null, "refresh_token", Map.of(
                "grant_type", "refresh_token",
                "client_id", "cid",
                "refresh_token", rtRaw
        ))).isInstanceOf(OAuthException.class)
                .hasFieldOrPropertyWithValue("error", "invalid_grant");

        verify(refreshTokenRepository, atLeastOnce()).lockFamily(familyId);
        verify(refreshTokenRepository).revokeFamily(eq(familyId), any());
        verify(mcpTokenRepository).revokeFamily(eq(familyId), any());
    }

    @Test
    void authCodeReuseRevokesFamily() {
        OAuthClient client = new OAuthClient();
        client.setClientId("cid");
        client.setTokenEndpointAuthMethod("none");
        when(clientRepository.findByClientId("cid")).thenReturn(Optional.of(client));

        UUID familyId = UUID.randomUUID();
        String codeRaw = "code_raw_secret";
        String codeHash = OAuthService.hash(codeRaw);

        OAuthAuthorizationCode authCode = new OAuthAuthorizationCode();
        authCode.setId(UUID.randomUUID());
        authCode.setCodeHash(codeHash);
        authCode.setClientId("cid");
        authCode.setRedirectUri("https://chatgpt.com/callback");
        authCode.setUserId(userId);
        authCode.setTenantId(tenantId);
        authCode.setCodeChallenge(OAuthService.base64UrlSha256("verifier123"));
        authCode.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        authCode.setUsedAt(Instant.now());
        authCode.setFamilyId(familyId);

        when(authCodeRepository.findByCodeHash(codeHash)).thenReturn(Optional.of(authCode));

        Map<String, String> params = Map.of(
                "grant_type", "authorization_code",
                "client_id", "cid",
                "code", codeRaw,
                "redirect_uri", "https://chatgpt.com/callback",
                "code_verifier", "verifier123"
        );

        assertThatThrownBy(() -> oAuthService.handleToken(null, "authorization_code", params))
                .isInstanceOf(OAuthException.class)
                .hasFieldOrPropertyWithValue("error", "invalid_grant");

        verify(refreshTokenRepository).lockFamily(familyId);
        verify(refreshTokenRepository).revokeFamily(eq(familyId), any());
        verify(mcpTokenRepository).revokeFamily(eq(familyId), any());
    }

    @Test
    void authCodeConcurrentReuseWhenMarkUsedReturnsZeroRevokesFamily() {
        OAuthClient client = new OAuthClient();
        client.setClientId("cid");
        client.setTokenEndpointAuthMethod("none");
        when(clientRepository.findByClientId("cid")).thenReturn(Optional.of(client));

        UUID familyId = UUID.randomUUID();
        String codeRaw = "code_raw_secret";
        String codeHash = OAuthService.hash(codeRaw);

        OAuthAuthorizationCode authCode = new OAuthAuthorizationCode();
        UUID codeId = UUID.randomUUID();
        authCode.setId(codeId);
        authCode.setCodeHash(codeHash);
        authCode.setClientId("cid");
        authCode.setRedirectUri("https://chatgpt.com/callback");
        authCode.setUserId(userId);
        authCode.setTenantId(tenantId);
        authCode.setCodeChallenge(OAuthService.base64UrlSha256("verifier123"));
        authCode.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        authCode.setUsedAt(null); // not marked yet in initial entity
        authCode.setFamilyId(null);

        when(authCodeRepository.findByCodeHash(codeHash)).thenReturn(Optional.of(authCode));
        // Concurrent thread already updated it -> returns 0
        when(authCodeRepository.markUsed(eq(codeId), any(), any())).thenReturn(0);
        // Direct query to database returns the winning thread's familyId
        when(authCodeRepository.findFamilyIdById(codeId)).thenReturn(Optional.of(familyId));

        Map<String, String> params = Map.of(
                "grant_type", "authorization_code",
                "client_id", "cid",
                "code", codeRaw,
                "redirect_uri", "https://chatgpt.com/callback",
                "code_verifier", "verifier123"
        );

        assertThatThrownBy(() -> oAuthService.handleToken(null, "authorization_code", params))
                .isInstanceOf(OAuthException.class)
                .hasFieldOrPropertyWithValue("error", "invalid_grant");

        verify(refreshTokenRepository).lockFamily(familyId);
        verify(refreshTokenRepository).revokeFamily(eq(familyId), any());
        verify(mcpTokenRepository).revokeFamily(eq(familyId), any());
    }

    @Test
    void clientAuthenticationRejectsMultipleChannelsAndEnforcesRegisteredMethod() {
        // 1. Rejects multiple channels simultaneously
        String basicHeader = "Basic " + Base64.getEncoder().encodeToString("cid:secret1".getBytes());
        assertThatThrownBy(() -> oAuthService.handleToken(basicHeader, "authorization_code", Map.of(
                "client_id", "cid",
                "client_secret", "secret2"
        ))).isInstanceOf(OAuthException.class)
                .hasFieldOrPropertyWithValue("error", "invalid_client");

        // 2. Client registered with client_secret_basic rejects POST body secret
        OAuthClient basicClient = new OAuthClient();
        basicClient.setClientId("basic_client");
        basicClient.setTokenEndpointAuthMethod("client_secret_basic");
        basicClient.setClientSecretHash(OAuthService.hash("correct_sec"));
        when(clientRepository.findByClientId("basic_client")).thenReturn(Optional.of(basicClient));

        assertThatThrownBy(() -> oAuthService.handleToken(null, "authorization_code", Map.of(
                "client_id", "basic_client",
                "client_secret", "correct_sec"
        ))).isInstanceOf(OAuthException.class)
                .hasFieldOrPropertyWithValue("error", "invalid_client");

        // 3. Client registered with client_secret_post rejects Basic header
        OAuthClient postClient = new OAuthClient();
        postClient.setClientId("post_client");
        postClient.setTokenEndpointAuthMethod("client_secret_post");
        postClient.setClientSecretHash(OAuthService.hash("correct_sec"));
        when(clientRepository.findByClientId("post_client")).thenReturn(Optional.of(postClient));

        String postHeader = "Basic " + Base64.getEncoder().encodeToString("post_client:correct_sec".getBytes());
        assertThatThrownBy(() -> oAuthService.handleToken(postHeader, "authorization_code", Map.of(
                "client_id", "post_client"
        ))).isInstanceOf(OAuthException.class)
                .hasFieldOrPropertyWithValue("error", "invalid_client");

        // 4. Client registered with none rejects secrets
        OAuthClient publicClient = new OAuthClient();
        publicClient.setClientId("pub_client");
        publicClient.setTokenEndpointAuthMethod("none");
        when(clientRepository.findByClientId("pub_client")).thenReturn(Optional.of(publicClient));

        assertThatThrownBy(() -> oAuthService.handleToken(null, "authorization_code", Map.of(
                "client_id", "pub_client",
                "client_secret", "some_secret"
        ))).isInstanceOf(OAuthException.class)
                .hasFieldOrPropertyWithValue("error", "invalid_client");
    }

    @Test
    void revokeConnectionInvalidatesTokensForClient() {
        UUID familyId = UUID.randomUUID();
        when(refreshTokenRepository.findActiveFamilyIdsForClient(tenantId, userId, "cid")).thenReturn(List.of(familyId));

        oAuthService.revokeConnection(tenantId, userId, "cid");

        verify(refreshTokenRepository).lockFamily(familyId);
        verify(refreshTokenRepository).revokeAllForClient(eq(tenantId), eq(userId), eq("cid"), any());
        verify(mcpTokenRepository).revokeAllForClient(eq(tenantId), eq(userId), eq("cid"), any());
    }
}
