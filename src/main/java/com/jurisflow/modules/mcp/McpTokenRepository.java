package com.jurisflow.modules.mcp;

import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.*;

public interface McpTokenRepository extends JpaRepository<McpToken, UUID> {
    Optional<McpToken> findByTokenHash(String hash);
    Optional<McpToken> findByIdAndTenantId(UUID id, UUID tenantId);
    List<McpToken> findByTenantIdAndUserIdOrderByCreatedAtDesc(UUID tenantId, UUID userId);
    List<McpToken> findByTenantIdAndUserIdAndKindOrderByCreatedAtDesc(UUID tenantId, UUID userId, String kind);
    List<McpToken> findByTenantIdAndUserIdAndKind(UUID tenantId, UUID userId, String kind);
    @Query("SELECT COUNT(t) FROM McpToken t WHERE t.tenantId = :tenantId AND t.userId = :userId AND t.kind = 'PAT' AND t.revokedAt IS NULL AND (t.expiresAt IS NULL OR t.expiresAt > :now)")
    long countActive(UUID tenantId, UUID userId, Instant now);
    @Modifying
    @Query("UPDATE McpToken t SET t.lastUsedAt = :now WHERE t.id = :id AND (t.lastUsedAt IS NULL OR t.lastUsedAt <= :cutoff)")
    int touch(UUID id, Instant now, Instant cutoff);
    @Modifying
    @Query("UPDATE McpToken t SET t.revokedAt = :now WHERE t.tenantId = :tenantId AND t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAll(UUID tenantId, UUID userId, Instant now);
    @Modifying
    @Query("UPDATE McpToken t SET t.revokedAt = :now WHERE t.oauthFamilyId = :familyId AND t.revokedAt IS NULL")
    int revokeFamily(UUID familyId, Instant now);
    @Modifying
    @Query("UPDATE McpToken t SET t.revokedAt = :now WHERE t.tenantId = :tenantId AND t.userId = :userId AND t.oauthClientId = :clientId AND t.revokedAt IS NULL")
    int revokeAllForClient(UUID tenantId, UUID userId, String clientId, Instant now);
    @Modifying
    @Query("DELETE FROM McpToken t WHERE t.kind = 'OAUTH' AND t.expiresAt < :cutoff")
    int deleteExpiredOAuthTokens(Instant cutoff);
}
