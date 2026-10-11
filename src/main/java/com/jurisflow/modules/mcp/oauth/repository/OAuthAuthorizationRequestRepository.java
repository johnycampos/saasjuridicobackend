package com.jurisflow.modules.mcp.oauth.repository;

import com.jurisflow.modules.mcp.oauth.entity.OAuthAuthorizationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthAuthorizationRequestRepository extends JpaRepository<OAuthAuthorizationRequest, UUID> {
    Optional<OAuthAuthorizationRequest> findByRequestHash(String requestHash);

    @Modifying
    @Query("UPDATE OAuthAuthorizationRequest r SET r.consumedAt = :now WHERE r.id = :id AND r.consumedAt IS NULL")
    int consumeRequest(UUID id, Instant now);

    @Modifying
    @Query("DELETE FROM OAuthAuthorizationRequest r WHERE r.expiresAt < :cutoff")
    int deleteExpired(Instant cutoff);
}
