package com.jurisflow.modules.mcp.oauth.repository;

import com.jurisflow.modules.mcp.oauth.entity.OAuthClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthClientRepository extends JpaRepository<OAuthClient, UUID> {
    Optional<OAuthClient> findByClientId(String clientId);
    boolean existsByClientId(String clientId);
}
