package com.jurisflow.modules.mcp.oauth.repository;

import com.jurisflow.modules.mcp.oauth.entity.OAuthAuthorizationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthAuthorizationCodeRepository extends JpaRepository<OAuthAuthorizationCode, UUID> {
    Optional<OAuthAuthorizationCode> findByCodeHash(String codeHash);

    @Modifying
    @Query("UPDATE OAuthAuthorizationCode c SET c.usedAt = :now, c.familyId = :familyId WHERE c.id = :id AND c.usedAt IS NULL")
    int markUsed(UUID id, Instant now, UUID familyId);

    @Modifying
    @Query("DELETE FROM OAuthAuthorizationCode c WHERE c.expiresAt < :cutoff")
    int deleteExpired(Instant cutoff);

    @Query(value = "SELECT family_id FROM oauth_authorization_codes WHERE id = :id", nativeQuery = true)
    Optional<UUID> findFamilyIdById(UUID id);
}
