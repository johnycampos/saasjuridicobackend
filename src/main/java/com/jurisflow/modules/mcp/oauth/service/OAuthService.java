package com.jurisflow.modules.mcp.oauth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jurisflow.modules.mcp.McpToken;
import com.jurisflow.modules.mcp.McpTokenRepository;
import com.jurisflow.modules.mcp.oauth.dto.*;
import com.jurisflow.modules.mcp.oauth.entity.*;
import com.jurisflow.modules.mcp.oauth.exception.OAuthException;
import com.jurisflow.modules.mcp.oauth.repository.*;
import com.jurisflow.modules.tenant.Tenant;
import com.jurisflow.modules.tenant.TenantMember;
import com.jurisflow.modules.tenant.TenantMemberRepository;
import com.jurisflow.modules.tenant.TenantRepository;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final OAuthClientRepository clientRepository;
    private final OAuthAuthorizationRequestRepository authRequestRepository;
    private final OAuthAuthorizationCodeRepository authCodeRepository;
    private final OAuthRefreshTokenRepository refreshTokenRepository;
    private final McpTokenRepository mcpTokenRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.public-backend-url:http://localhost:8080}")
    private String publicBackendUrl;

    @Value("${app.frontend-url:http://localhost}")
    private String frontendUrl;

    private static final SecureRandom RANDOM = new SecureRandom();
    private final ConcurrentHashMap<String, DcrWindow> dcrRateLimits = new ConcurrentHashMap<>();
    private record DcrWindow(long hour, int count) {}

    public String getPublicBackendUrl() {
        return publicBackendUrl != null ? publicBackendUrl.replaceAll("/+$", "") : "http://localhost:8080";
    }

    public String getFrontendUrl() {
        return frontendUrl != null ? frontendUrl.replaceAll("/+$", "") : "http://localhost";
    }

    // --- B4. Dynamic Client Registration (RFC 7591) ---
    @Transactional
    public DcrResponse registerClient(DcrRequest request, String clientIp) {
        checkDcrRateLimit(clientIp);

        if (request == null || request.redirect_uris() == null || request.redirect_uris().isEmpty()
                || request.redirect_uris().size() > 5) {
            throw new OAuthException("invalid_redirect_uri", "Deve conter entre 1 e 5 redirect_uris");
        }

        for (String uri : request.redirect_uris()) {
            if (!isValidRedirectUri(uri)) {
                throw new OAuthException("invalid_redirect_uri", "URI de redirecionamento inválida: " + uri);
            }
        }

        String authMethod = request.token_endpoint_auth_method() != null
                ? request.token_endpoint_auth_method() : "none";
        if (!Set.of("none", "client_secret_post", "client_secret_basic").contains(authMethod)) {
            throw new OAuthException("invalid_client_metadata", "token_endpoint_auth_method não suportado");
        }

        String clientId = randomBase64Url(32);
        String clientSecret = null;
        String clientSecretHash = null;

        if (!"none".equals(authMethod)) {
            clientSecret = randomBase64Url(32);
            clientSecretHash = hash(clientSecret);
        }

        String clientName = request.client_name();
        if (clientName != null && clientName.length() > 200) {
            clientName = clientName.substring(0, 200);
        }

        OAuthClient client = new OAuthClient();
        client.setClientId(clientId);
        client.setClientSecretHash(clientSecretHash);
        client.setClientName(clientName);
        client.setTokenEndpointAuthMethod(authMethod);
        try {
            client.setRedirectUris(objectMapper.writeValueAsString(request.redirect_uris()));
        } catch (Exception e) {
            throw new OAuthException("invalid_client_metadata", "Erro ao serializar redirect_uris");
        }

        clientRepository.save(client);

        long nowSec = Instant.now().getEpochSecond();
        return new DcrResponse(
                clientId,
                clientSecret,
                nowSec,
                0L,
                clientName,
                request.redirect_uris(),
                List.of("authorization_code", "refresh_token"),
                List.of("code"),
                authMethod
        );
    }

    private void checkDcrRateLimit(String clientIp) {
        long hour = System.currentTimeMillis() / 3600000;
        dcrRateLimits.entrySet().removeIf(e -> e.getValue().hour() < hour);
        String key = (clientIp != null && !clientIp.isBlank()) ? clientIp : "unknown";
        DcrWindow window = dcrRateLimits.compute(key, (ip, old) ->
                new DcrWindow(hour, old == null || old.hour() != hour ? 1 : old.count() + 1));
        if (window.count() > 20) {
            throw new OAuthException("invalid_client_metadata", "Limite de registros de clientes excedido (máx 20/hora)");
        }
    }

    public boolean isValidRedirectUri(String uriStr) {
        if (uriStr == null || uriStr.isBlank() || uriStr.length() > 500 || uriStr.contains("#")) {
            return false;
        }
        try {
            URI u = URI.create(uriStr);
            String scheme = u.getScheme();
            String host = u.getHost();
            if ("https".equalsIgnoreCase(scheme)) {
                return host != null && !host.isBlank();
            }
            if ("http".equalsIgnoreCase(scheme)) {
                return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equalsIgnoreCase(host);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // --- B5. Authorization Initiation ---
    public record AuthorizeResult(String redirectUrl, boolean isDirectHtmlError, String htmlErrorMessage) {}

    @Transactional
    public AuthorizeResult handleAuthorize(String responseType, String clientId, String redirectUri,
                                           String codeChallenge, String codeChallengeMethod,
                                           String state, String scope, String resource) {
        if (clientId == null || clientId.isBlank() || redirectUri == null || redirectUri.isBlank()) {
            return new AuthorizeResult(null, true, "Parâmetros client_id e redirect_uri são obrigatórios.");
        }

        var clientOpt = clientRepository.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return new AuthorizeResult(null, true, "Cliente OAuth não encontrado.");
        }

        OAuthClient client = clientOpt.get();
        List<String> registeredUris;
        try {
            registeredUris = objectMapper.readValue(client.getRedirectUris(), new TypeReference<>() {});
        } catch (Exception e) {
            registeredUris = List.of();
        }

        if (!registeredUris.contains(redirectUri)) {
            return new AuthorizeResult(null, true, "URI de redirecionamento não registrada para este cliente.");
        }

        // Redirect URI and client_id are validated. Next validate other params and redirect with error if invalid.
        String expectedResource = getPublicBackendUrl() + "/mcp";
        if (!"code".equals(responseType)) {
            return new AuthorizeResult(buildRedirectError(redirectUri, "unsupported_response_type", "response_type deve ser 'code'", state), false, null);
        }
        if (codeChallenge == null || codeChallenge.isBlank()) {
            return new AuthorizeResult(buildRedirectError(redirectUri, "invalid_request", "code_challenge é obrigatório", state), false, null);
        }
        if (!"S256".equals(codeChallengeMethod)) {
            return new AuthorizeResult(buildRedirectError(redirectUri, "invalid_request", "code_challenge_method deve ser 'S256'", state), false, null);
        }
        if (scope != null && !scope.isBlank() && !Set.of("mcp").contains(scope.trim())) {
            return new AuthorizeResult(buildRedirectError(redirectUri, "invalid_scope", "Escopo solicitado não suportado", state), false, null);
        }
        if (resource != null && !resource.isBlank() && !expectedResource.equals(resource.trim())) {
            return new AuthorizeResult(buildRedirectError(redirectUri, "invalid_target", "Recurso não suportado", state), false, null);
        }

        // Valid! Create opaque authorization request and redirect to frontend consent page.
        String opaqueReqId = randomBase64Url(32);
        OAuthAuthorizationRequest authReq = new OAuthAuthorizationRequest();
        authReq.setRequestHash(hash(opaqueReqId));
        authReq.setClientId(clientId);
        authReq.setRedirectUri(redirectUri);
        authReq.setState(state);
        authReq.setScope(scope != null && !scope.isBlank() ? scope.trim() : "mcp");
        authReq.setResource(expectedResource);
        authReq.setCodeChallenge(codeChallenge);
        authReq.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));

        authRequestRepository.save(authReq);

        String consentUrl = getFrontendUrl() + "/oauth/autorizar?req=" + urlEncode(opaqueReqId);
        return new AuthorizeResult(consentUrl, false, null);
    }

    private String buildRedirectError(String redirectUri, String error, String description, String state) {
        StringBuilder sb = new StringBuilder(redirectUri);
        sb.append(redirectUri.contains("?") ? "&" : "?");
        sb.append("error=").append(urlEncode(error));
        if (description != null) {
            sb.append("&error_description=").append(urlEncode(description));
        }
        if (state != null) {
            sb.append("&state=").append(urlEncode(state));
        }
        return sb.toString();
    }

    // --- B6. Consent APIs ---
    public OAuthConsentDetailsResponse getConsentDetails(String opaqueReqId, UUID userId) {
        var authReq = authRequestRepository.findByRequestHash(hash(opaqueReqId))
                .filter(r -> r.getConsumedAt() == null && r.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> BusinessException.notFound("Requisição de autorização"));

        var client = clientRepository.findByClientId(authReq.getClientId())
                .orElseThrow(() -> BusinessException.notFound("Cliente OAuth"));

        String redirectHost = "";
        try {
            redirectHost = URI.create(authReq.getRedirectUri()).getHost();
        } catch (Exception ignored) {}

        var members = tenantMemberRepository.findByUser_IdAndAtivoTrue(userId);
        var tenantIds = members.stream().map(TenantMember::getTenantId).toList();
        Map<UUID, String> tenantNames = tenantRepository.findAllById(tenantIds).stream()
                .collect(Collectors.toMap(Tenant::getId, Tenant::getNome));

        List<OAuthConsentDetailsResponse.TenantOption> tenants = members.stream()
                .map(m -> new OAuthConsentDetailsResponse.TenantOption(
                        m.getTenantId(),
                        tenantNames.getOrDefault(m.getTenantId(), "Escritório"),
                        m.getRole().name()
                )).toList();

        return new OAuthConsentDetailsResponse(
                client.getClientName() != null ? client.getClientName() : client.getClientId(),
                redirectHost != null ? redirectHost : "",
                authReq.getScope(),
                tenants
        );
    }

    @Transactional
    public OAuthRedirectResponse approveConsent(String opaqueReqId, UUID tenantId, UUID userId) {
        var authReq = authRequestRepository.findByRequestHash(hash(opaqueReqId))
                .filter(r -> r.getConsumedAt() == null && r.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> BusinessException.notFound("Requisição de autorização"));

        if (!tenantMemberRepository.existsByTenantIdAndUser_IdAndAtivoTrue(tenantId, userId)) {
            throw BusinessException.forbidden();
        }

        int updated = authRequestRepository.consumeRequest(authReq.getId(), Instant.now());
        if (updated == 0) {
            throw new BusinessException("Requisição de autorização já consumida ou expirada");
        }

        String code = randomBase64Url(32);
        OAuthAuthorizationCode authCode = new OAuthAuthorizationCode();
        authCode.setCodeHash(hash(code));
        authCode.setClientId(authReq.getClientId());
        authCode.setUserId(userId);
        authCode.setTenantId(tenantId);
        authCode.setRedirectUri(authReq.getRedirectUri());
        authCode.setCodeChallenge(authReq.getCodeChallenge());
        authCode.setScope(authReq.getScope());
        authCode.setResource(authReq.getResource());
        authCode.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));

        authCodeRepository.save(authCode);

        StringBuilder sb = new StringBuilder(authReq.getRedirectUri());
        sb.append(authReq.getRedirectUri().contains("?") ? "&" : "?");
        sb.append("code=").append(urlEncode(code));
        if (authReq.getState() != null) {
            sb.append("&state=").append(urlEncode(authReq.getState()));
        }
        sb.append("&iss=").append(urlEncode(getPublicBackendUrl()));

        return new OAuthRedirectResponse(sb.toString());
    }

    @Transactional
    public OAuthRedirectResponse denyConsent(String opaqueReqId, UUID userId) {
        var authReq = authRequestRepository.findByRequestHash(hash(opaqueReqId))
                .filter(r -> r.getConsumedAt() == null && r.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> BusinessException.notFound("Requisição de autorização"));

        authRequestRepository.consumeRequest(authReq.getId(), Instant.now());

        StringBuilder sb = new StringBuilder(authReq.getRedirectUri());
        sb.append(authReq.getRedirectUri().contains("?") ? "&" : "?");
        sb.append("error=access_denied");
        if (authReq.getState() != null) {
            sb.append("&state=").append(urlEncode(authReq.getState()));
        }
        return new OAuthRedirectResponse(sb.toString());
    }

    // --- B7. Token Endpoint ---
    @Transactional(noRollbackFor = OAuthException.class)
    public OAuthTokenResponse handleToken(String authHeader, String grantType, Map<String, String> params) {
        String basicClientId = null;
        String basicClientSecret = null;
        boolean hasBasic = false;

        if (authHeader != null && authHeader.toLowerCase().startsWith("basic ")) {
            hasBasic = true;
            try {
                String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6).trim()), StandardCharsets.UTF_8);
                int colon = decoded.indexOf(':');
                if (colon >= 0) {
                    basicClientId = decoded.substring(0, colon);
                    basicClientSecret = decoded.substring(colon + 1);
                } else {
                    basicClientId = decoded;
                }
            } catch (Exception e) {
                throw new OAuthException("invalid_client", "Cabeçalho de autenticação Basic inválido", HttpStatus.UNAUTHORIZED);
            }
        }

        String postClientId = params.get("client_id");
        String postClientSecret = params.get("client_secret");
        boolean hasPostSecret = postClientSecret != null && !postClientSecret.isBlank();

        // Rejeita credenciais em múltiplos canais simultâneos (RFC 6749 2.3)
        if (hasBasic && hasPostSecret) {
            throw new OAuthException("invalid_client", "Credenciais de cliente enviadas em múltiplos canais simultâneos", HttpStatus.UNAUTHORIZED);
        }
        if (hasBasic && postClientId != null && !postClientId.isBlank() && !postClientId.equals(basicClientId)) {
            throw new OAuthException("invalid_client", "client_id divergente entre cabeçalho e corpo", HttpStatus.UNAUTHORIZED);
        }

        String clientId = hasBasic && basicClientId != null && !basicClientId.isBlank() ? basicClientId : postClientId;
        if (clientId == null || clientId.isBlank()) {
            throw new OAuthException("invalid_client", "client_id não informado", HttpStatus.UNAUTHORIZED);
        }

        OAuthClient client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new OAuthException("invalid_client", "Cliente desconhecido", HttpStatus.UNAUTHORIZED));

        String authMethod = client.getTokenEndpointAuthMethod() != null ? client.getTokenEndpointAuthMethod() : "none";

        switch (authMethod) {
            case "client_secret_basic" -> {
                if (!hasBasic || basicClientSecret == null || basicClientSecret.isBlank()) {
                    throw new OAuthException("invalid_client", "Cliente registrado com client_secret_basic exige autenticação via cabeçalho Basic", HttpStatus.UNAUTHORIZED);
                }
                String givenHash = hash(basicClientSecret);
                String expectedHash = client.getClientSecretHash() != null ? client.getClientSecretHash() : "";
                if (!MessageDigest.isEqual(givenHash.getBytes(StandardCharsets.UTF_8), expectedHash.getBytes(StandardCharsets.UTF_8))) {
                    throw new OAuthException("invalid_client", "Credenciais do cliente inválidas", HttpStatus.UNAUTHORIZED);
                }
            }
            case "client_secret_post" -> {
                if (hasBasic) {
                    throw new OAuthException("invalid_client", "Cliente registrado com client_secret_post não aceita cabeçalho Basic", HttpStatus.UNAUTHORIZED);
                }
                if (!hasPostSecret) {
                    throw new OAuthException("invalid_client", "client_secret obrigatório no corpo para client_secret_post", HttpStatus.UNAUTHORIZED);
                }
                String givenHash = hash(postClientSecret);
                String expectedHash = client.getClientSecretHash() != null ? client.getClientSecretHash() : "";
                if (!MessageDigest.isEqual(givenHash.getBytes(StandardCharsets.UTF_8), expectedHash.getBytes(StandardCharsets.UTF_8))) {
                    throw new OAuthException("invalid_client", "Credenciais do cliente inválidas", HttpStatus.UNAUTHORIZED);
                }
            }
            case "none" -> {
                if (hasBasic && basicClientSecret != null && !basicClientSecret.isBlank()) {
                    throw new OAuthException("invalid_client", "Cliente público não deve enviar secret", HttpStatus.UNAUTHORIZED);
                }
                if (hasPostSecret) {
                    throw new OAuthException("invalid_client", "Cliente público não deve enviar secret", HttpStatus.UNAUTHORIZED);
                }
            }
            default -> throw new OAuthException("invalid_client", "Método de autenticação de cliente desconhecido", HttpStatus.UNAUTHORIZED);
        }

        if ("authorization_code".equals(grantType)) {
            return exchangeAuthorizationCode(client, params);
        } else if ("refresh_token".equals(grantType)) {
            return exchangeRefreshToken(client, params);
        } else {
            throw new OAuthException("unsupported_grant_type", "grant_type não suportado: " + grantType);
        }
    }

    private OAuthTokenResponse exchangeAuthorizationCode(OAuthClient client, Map<String, String> params) {
        String code = params.get("code");
        String redirectUri = params.get("redirect_uri");
        String codeVerifier = params.get("code_verifier");

        if (code == null || code.isBlank() || redirectUri == null || redirectUri.isBlank()
                || codeVerifier == null || codeVerifier.isBlank()) {
            throw new OAuthException("invalid_request", "code, redirect_uri e code_verifier são obrigatórios");
        }

        String codeHash = hash(code);
        OAuthAuthorizationCode authCode = authCodeRepository.findByCodeHash(codeHash)
                .orElseThrow(() -> new OAuthException("invalid_grant", "Código de autorização inválido"));

        if (!authCode.getClientId().equals(client.getClientId())) {
            throw new OAuthException("invalid_grant", "Código não pertence a este cliente");
        }
        if (!authCode.getRedirectUri().equals(redirectUri)) {
            throw new OAuthException("invalid_grant", "redirect_uri divergente");
        }
        if (authCode.getExpiresAt().isBefore(Instant.now())) {
            throw new OAuthException("invalid_grant", "Código de autorização expirado");
        }

        if (authCode.getUsedAt() != null) {
            log.warn("Detectada tentativa de reutilização de code hash {}", codeHash);
            if (authCode.getFamilyId() != null) {
                revokeFamilyInternal(authCode.getFamilyId());
            }
            throw new OAuthException("invalid_grant", "Código de autorização já foi utilizado");
        }

        // Validate PKCE: S256(code_verifier) == code_challenge
        String computedChallenge = base64UrlSha256(codeVerifier);
        if (!computedChallenge.equals(authCode.getCodeChallenge())) {
            throw new OAuthException("invalid_grant", "PKCE code_verifier não confere com o code_challenge");
        }

        UUID familyId = UUID.randomUUID();
        int updated = authCodeRepository.markUsed(authCode.getId(), Instant.now(), familyId);
        if (updated == 0) {
            authCodeRepository.findFamilyIdById(authCode.getId())
                    .ifPresent(this::revokeFamilyInternal);
            throw new OAuthException("invalid_grant", "Código de autorização já utilizado");
        }

        if (!tenantMemberRepository.existsByTenantIdAndUser_IdAndAtivoTrue(authCode.getTenantId(), authCode.getUserId())) {
            throw new OAuthException("invalid_grant", "Usuário não é membro ativo do escritório");
        }

        return issueTokenPair(client, authCode.getTenantId(), authCode.getUserId(), familyId, authCode.getScope(), authCode.getResource());
    }

    private OAuthTokenResponse exchangeRefreshToken(OAuthClient client, Map<String, String> params) {
        String refreshTokenRaw = params.get("refresh_token");
        if (refreshTokenRaw == null || refreshTokenRaw.isBlank()) {
            throw new OAuthException("invalid_request", "refresh_token é obrigatório");
        }

        String tokenHash = hash(refreshTokenRaw);
        OAuthRefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new OAuthException("invalid_grant", "Refresh token inválido"));

        if (!refreshToken.getClientId().equals(client.getClientId())) {
            throw new OAuthException("invalid_grant", "Refresh token não pertence a este cliente");
        }

        UUID familyId = refreshToken.getFamilyId();
        // Serialização por família para evitar concorrência entre rotação e revogação
        refreshTokenRepository.lockFamily(familyId);

        // Sob o lock, recarrega e revalida o refresh token
        OAuthRefreshToken lockedToken = refreshTokenRepository.findById(refreshToken.getId())
                .orElseThrow(() -> new OAuthException("invalid_grant", "Refresh token inválido"));

        if (lockedToken.getRevokedAt() != null || lockedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new OAuthException("invalid_grant", "Refresh token revogado ou expirado");
        }

        if (lockedToken.getUsedAt() != null) {
            log.warn("Reuso de refresh token detectado para familyId={}. Revogando família inteira.", familyId);
            revokeFamilyLocked(familyId);
            throw new OAuthException("invalid_grant", "Reuso de refresh token detectado");
        }

        int updated = refreshTokenRepository.markUsed(lockedToken.getId(), Instant.now());
        if (updated == 0) {
            revokeFamilyLocked(familyId);
            throw new OAuthException("invalid_grant", "Reuso de refresh token detectado");
        }

        if (!tenantMemberRepository.existsByTenantIdAndUser_IdAndAtivoTrue(lockedToken.getTenantId(), lockedToken.getUserId())) {
            throw new OAuthException("invalid_grant", "Usuário não é membro ativo do escritório");
        }

        return issueTokenPair(client, lockedToken.getTenantId(), lockedToken.getUserId(),
                familyId, lockedToken.getScope(), lockedToken.getResource());
    }

    private OAuthTokenResponse issueTokenPair(OAuthClient client, UUID tenantId, UUID userId,
                                              UUID familyId, String scope, String resource) {
        Instant now = Instant.now();

        // 1. Issue Access Token: format jfmcp_ + 32 bytes, expires in 1 hour
        String rawAccessToken = "jfmcp_" + randomBase64Url(32);
        McpToken accessToken = new McpToken();
        accessToken.setTenantId(tenantId);
        accessToken.setUserId(userId);
        accessToken.setKind("OAUTH");
        accessToken.setOauthClientId(client.getClientId());
        accessToken.setOauthFamilyId(familyId);
        String tokenName = client.getClientName() != null ? client.getClientName() : client.getClientId();
        accessToken.setNome(tokenName.length() > 100 ? tokenName.substring(0, 100) : tokenName);
        accessToken.setTokenHash(hash(rawAccessToken));
        accessToken.setTokenPrefix(rawAccessToken.substring(0, 12));
        accessToken.setExpiresAt(now.plus(1, ChronoUnit.HOURS));
        mcpTokenRepository.save(accessToken);

        // 2. Issue Refresh Token: format jfrt_ + 32 bytes, expires in 30 days
        String rawRefreshToken = "jfrt_" + randomBase64Url(32);
        OAuthRefreshToken refreshToken = new OAuthRefreshToken();
        refreshToken.setTokenHash(hash(rawRefreshToken));
        refreshToken.setFamilyId(familyId);
        refreshToken.setClientId(client.getClientId());
        refreshToken.setUserId(userId);
        refreshToken.setTenantId(tenantId);
        refreshToken.setScope(scope != null ? scope : "mcp");
        refreshToken.setResource(resource);
        refreshToken.setExpiresAt(now.plus(30, ChronoUnit.DAYS));
        refreshTokenRepository.save(refreshToken);

        return new OAuthTokenResponse(
                rawAccessToken,
                "Bearer",
                3600L,
                rawRefreshToken,
                "mcp"
        );
    }

    // --- B8. Revoke (RFC 7009) ---
    @Transactional
    public void revokeToken(String tokenRaw) {
        if (tokenRaw == null || tokenRaw.isBlank()) return;
        String tokenHash = hash(tokenRaw);
        Instant now = Instant.now();

        // Check if it's an OAuth refresh token
        var refreshOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        if (refreshOpt.isPresent()) {
            UUID familyId = refreshOpt.get().getFamilyId();
            refreshTokenRepository.lockFamily(familyId);
            refreshTokenRepository.revokeFamily(familyId, now);
            mcpTokenRepository.revokeFamily(familyId, now);
            return;
        }

        // Check if it's an access token
        var mcpOpt = mcpTokenRepository.findByTokenHash(tokenHash);
        if (mcpOpt.isPresent() && mcpOpt.get().getOauthFamilyId() != null) {
            UUID familyId = mcpOpt.get().getOauthFamilyId();
            refreshTokenRepository.lockFamily(familyId);
            refreshTokenRepository.revokeFamily(familyId, now);
            mcpTokenRepository.revokeFamily(familyId, now);
        }
    }

    // --- B9. App Connections Management ---
    public List<OAuthConnectionResponse> listConnections(UUID tenantId, UUID userId) {
        var activeRefreshTokens = refreshTokenRepository.findByTenantIdAndUserIdAndRevokedAtIsNull(tenantId, userId);
        Map<String, List<OAuthRefreshToken>> byClient = new HashMap<>();
        for (var rt : activeRefreshTokens) {
            byClient.computeIfAbsent(rt.getClientId(), k -> new ArrayList<>()).add(rt);
        }

        var accessTokens = mcpTokenRepository.findByTenantIdAndUserIdAndKind(tenantId, userId, "OAUTH");
        Map<String, List<McpToken>> accessByClient = new HashMap<>();
        for (var at : accessTokens) {
            if (at.getOauthClientId() != null) {
                accessByClient.computeIfAbsent(at.getOauthClientId(), k -> new ArrayList<>()).add(at);
            }
        }

        List<OAuthConnectionResponse> responses = new ArrayList<>();
        for (Map.Entry<String, List<OAuthRefreshToken>> entry : byClient.entrySet()) {
            String cId = entry.getKey();
            var clientOpt = clientRepository.findByClientId(cId);
            String cName = clientOpt.map(c -> c.getClientName() != null ? c.getClientName() : cId).orElse(cId);

            // Min createdAt across tokens as conectadoEm
            Instant conectadoEm = entry.getValue().stream()
                    .map(OAuthRefreshToken::getCreatedAt)
                    .filter(Objects::nonNull)
                    .map(dt -> dt.atZone(java.time.ZoneId.systemDefault()).toInstant())
                    .min(Comparator.naturalOrder())
                    .orElse(Instant.now());

            // Max lastUsedAt across access tokens
            Instant ultimoUso = accessByClient.getOrDefault(cId, List.of()).stream()
                    .map(McpToken::getLastUsedAt)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);

            responses.add(new OAuthConnectionResponse(cId, cName, conectadoEm, ultimoUso));
        }

        responses.sort(Comparator.comparing(OAuthConnectionResponse::conectadoEm).reversed());
        return responses;
    }

    @Transactional
    public void revokeConnection(UUID tenantId, UUID userId, String clientId) {
        List<UUID> families = refreshTokenRepository.findActiveFamilyIdsForClient(tenantId, userId, clientId);
        for (UUID familyId : families) {
            refreshTokenRepository.lockFamily(familyId);
        }
        Instant now = Instant.now();
        refreshTokenRepository.revokeAllForClient(tenantId, userId, clientId, now);
        mcpTokenRepository.revokeAllForClient(tenantId, userId, clientId, now);
    }

    private void revokeFamilyInternal(UUID familyId) {
        if (familyId == null) return;
        refreshTokenRepository.lockFamily(familyId);
        revokeFamilyLocked(familyId);
    }

    private void revokeFamilyLocked(UUID familyId) {
        if (familyId == null) return;
        Instant now = Instant.now();
        refreshTokenRepository.revokeFamily(familyId, now);
        mcpTokenRepository.revokeFamily(familyId, now);
    }

    // Daily cleanup job:
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredOAuthData() {
        Instant now = Instant.now();
        int reqs = authRequestRepository.deleteExpired(now.minus(1, ChronoUnit.DAYS));
        int codes = authCodeRepository.deleteExpired(now.minus(1, ChronoUnit.DAYS));
        int access = mcpTokenRepository.deleteExpiredOAuthTokens(now.minus(7, ChronoUnit.DAYS));
        int refresh = refreshTokenRepository.deleteExpired(now.minus(30, ChronoUnit.DAYS));
        log.info("OAuth cleanup: removed {} requests, {} codes, {} access tokens, {} refresh tokens",
                reqs, codes, access, refresh);
    }

    // --- Helpers ---
    public static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    public static String base64UrlSha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    public static String randomBase64Url(int byteCount) {
        byte[] bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String urlEncode(String val) {
        return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }
}
