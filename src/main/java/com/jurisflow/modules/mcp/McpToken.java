package com.jurisflow.modules.mcp;

import com.jurisflow.shared.entity.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "mcp_tokens")
public class McpToken extends TenantEntity {
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false, length = 100)
    private String nome;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "token_prefix", nullable = false, length = 16)
    private String tokenPrefix;
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(nullable = false, length = 10)
    private String kind = "PAT";
    @Column(name = "oauth_client_id", length = 64)
    private String oauthClientId;
    @Column(name = "oauth_family_id")
    private UUID oauthFamilyId;
}
