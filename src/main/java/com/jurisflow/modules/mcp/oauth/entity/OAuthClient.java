package com.jurisflow.modules.mcp.oauth.entity;

import com.jurisflow.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "oauth_clients")
public class OAuthClient extends BaseEntity {

    @Column(name = "client_id", nullable = false, unique = true, length = 64)
    private String clientId;

    @Column(name = "client_secret_hash", length = 64)
    private String clientSecretHash;

    @Column(name = "client_name", length = 200)
    private String clientName;

    @Column(name = "redirect_uris", nullable = false, columnDefinition = "TEXT")
    private String redirectUris;

    @Column(name = "token_endpoint_auth_method", nullable = false, length = 30)
    private String tokenEndpointAuthMethod = "none";
}
