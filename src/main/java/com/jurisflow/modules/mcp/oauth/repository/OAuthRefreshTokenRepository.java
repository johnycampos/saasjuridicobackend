package com.jurisflow.modules.mcp.oauth.repository;

import com.jurisflow.modules.mcp.oauth.entity.OAuthRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthRefreshTokenRepository extends JpaRepository<OAuthRefreshToken, UUID> {
    Optional<OAuthRefreshToken> findByTokenHash(String tokenHash);

    List<OAuthRefreshToken> findByFamilyId(UUID familyId);

    List<OAuthRefreshToken> findByTenantIdAndUserIdAndRevokedAtIsNull(UUID tenantId, UUID userId);

    @Query(value = "SELECT pg_advisory_xact_lock(hashtext(CAST(:familyId AS text)))", nativeQuery = true)
    void lockFamily(@org.springframework.data.repository.query.Param("familyId") UUID familyId);

    @Query("SELECT DISTINCT r.familyId FROM OAuthRefreshToken r WHERE r.tenantId = :tenantId AND r.userId = :userId AND r.clientId = :clientId AND r.revokedAt IS NULL ORDER BY r.familyId")
    List<UUID> findActiveFamilyIdsForClient(@org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
                                            @org.springframework.data.repository.query.Param("userId") UUID userId,
                                            @org.springframework.data.repository.query.Param("clientId") String clientId);

    @Query("SELECT DISTINCT r.familyId FROM OAuthRefreshToken r WHERE r.tenantId = :tenantId AND r.userId = :userId AND r.revokedAt IS NULL ORDER BY r.familyId")
    List<UUID> findActiveFamilyIdsForUser(@org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
                                          @org.springframework.data.repository.query.Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE OAuthRefreshToken r SET r.usedAt = :now WHERE r.id = :id AND r.usedAt IS NULL AND r.revokedAt IS NULL AND r.expiresAt > :now")
    int markUsed(@org.springframework.data.repository.query.Param("id") UUID id, @org.springframework.data.repository.query.Param("now") Instant now);

    @Modifying
    @Query("UPDATE OAuthRefreshToken r SET r.revokedAt = :now WHERE r.familyId = :familyId AND r.revokedAt IS NULL")
    int revokeFamily(UUID familyId, Instant now);

    @Modifying
    @Query("UPDATE OAuthRefreshToken r SET r.revokedAt = :now WHERE r.tenantId = :tenantId AND r.userId = :userId AND r.clientId = :clientId AND r.revokedAt IS NULL")
    int revokeAllForClient(UUID tenantId, UUID userId, String clientId, Instant now);

    @Modifying
    @Query("UPDATE OAuthRefreshToken r SET r.revokedAt = :now WHERE r.tenantId = :tenantId AND r.userId = :userId AND r.revokedAt IS NULL")
    int revokeAll(UUID tenantId, UUID userId, Instant now);

    @Modifying
    @Query("DELETE FROM OAuthRefreshToken r WHERE r.expiresAt < :cutoff")
    int deleteExpired(Instant cutoff);
}
