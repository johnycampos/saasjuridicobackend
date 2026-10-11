package com.jurisflow.modules.mcp.oauth.entity;

import com.jurisflow.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "oauth_authorization_requests")
public class OAuthAuthorizationRequest extends BaseEntity {

    @Column(name = "request_hash", nullable = false, unique = true, length = 64)
    private String requestHash;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "redirect_uri", nullable = false, columnDefinition = "TEXT")
    private String redirectUri;

    @Column(name = "state", columnDefinition = "TEXT")
    private String state;

    @Column(name = "scope", length = 200)
    private String scope;

    @Column(name = "resource", columnDefinition = "TEXT")
    private String resource;

    @Column(name = "code_challenge", nullable = false, length = 128)
    private String codeChallenge;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;
}
