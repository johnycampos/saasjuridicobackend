package com.jurisflow.modules.mcp;

import com.jurisflow.modules.mcp.dto.*;
import com.jurisflow.modules.tenant.*;
import com.jurisflow.modules.user.UserRepository;
import com.jurisflow.security.*;
import com.jurisflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class McpTokenService {
    private final McpTokenRepository repository;
    private final TenantMemberRepository members;
    private final UserRepository users;
    private final TenantAccessGuard guard;
    private static final SecureRandom RANDOM = new SecureRandom();

    private UUID tenant(UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) throw new BusinessException("X-Tenant-ID é obrigatório");
        guard.requireActiveMember(tenantId, userId);
        return tenantId;
    }

    public List<McpTokenResponse> list(UUID userId) {
        return repository.findByTenantIdAndUserIdAndKindOrderByCreatedAtDesc(tenant(userId), userId, "PAT").stream()
            .map(t -> new McpTokenResponse(t.getId(), t.getNome(), t.getTokenPrefix(), t.getCreatedAt(),
                t.getLastUsedAt(), t.getExpiresAt(), t.getRevokedAt() != null)).toList();
    }

    @Transactional
    public CreatedMcpTokenResponse create(UUID userId, CreateMcpTokenRequest request) {
        UUID tenantId = tenant(userId);
        if (request.nome() == null || request.nome().isBlank() || request.nome().length() > 100
            || (request.expiresInDays() != null && (request.expiresInDays() < 1 || request.expiresInDays() > 365)))
            throw new BusinessException("Nome ou validade inválidos");
        // Serialize creation for this user, including requests from different application instances.
        users.lockForMcpTokenCreation(userId).orElseThrow(() -> BusinessException.notFound("Usuário"));
        Instant now = Instant.now();
        if (repository.countActive(tenantId, userId, now) >= 10)
            throw new BusinessException("Limite de 10 tokens ativos por usuário e escritório");
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = "jfmcp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        McpToken token = new McpToken();
        token.setTenantId(tenantId);
        token.setUserId(userId);
        token.setKind("PAT");
        token.setNome(request.nome().trim());
        token.setTokenHash(hash(raw));
        token.setTokenPrefix(raw.substring(0, 12));
        token.setExpiresAt(request.expiresInDays() == null ? null : now.plus(request.expiresInDays(), ChronoUnit.DAYS));
        token = repository.save(token);
        return new CreatedMcpTokenResponse(token.getId(), token.getNome(), raw, token.getTokenPrefix(), token.getExpiresAt());
    }

    @Transactional
    public void revoke(UUID id, UUID userId) {
        UUID tenantId = tenant(userId);
        McpToken token = repository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> BusinessException.notFound("Token"));
        if (!token.getUserId().equals(userId)) guard.requireMinRole(tenantId, userId, TenantRole.ADMIN);
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(Instant.now());
            repository.save(token);
        }
    }

    public Optional<McpToken> authenticate(String raw) {
        if (raw == null || !raw.matches("jfmcp_[A-Za-z0-9_-]{43}")) return Optional.empty();
        return repository.findByTokenHash(hash(raw)).filter(t -> t.getRevokedAt() == null)
            .filter(t -> t.getExpiresAt() == null || t.getExpiresAt().isAfter(Instant.now()))
            .filter(t -> members.existsByTenantIdAndUser_IdAndAtivoTrue(t.getTenantId(), t.getUserId()));
    }

    public UserPrincipal principal(McpToken token) {
        return UserPrincipal.fromUser(users.findById(token.getUserId())
            .orElseThrow(() -> BusinessException.notFound("Usuário")));
    }

    @Transactional
    public void touch(McpToken token) {
        Instant now = Instant.now();
        if (token.getLastUsedAt() == null || !token.getLastUsedAt().isAfter(now.minusSeconds(60)))
            repository.touch(token.getId(), now, now.minusSeconds(60));
    }

    public static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
