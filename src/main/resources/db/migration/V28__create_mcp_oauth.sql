CREATE TABLE oauth_clients (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  client_id VARCHAR(64) NOT NULL UNIQUE,
  client_secret_hash VARCHAR(64),
  client_name VARCHAR(200),
  redirect_uris TEXT NOT NULL,
  token_endpoint_auth_method VARCHAR(30) NOT NULL DEFAULT 'none',
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE oauth_authorization_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  request_hash VARCHAR(64) NOT NULL UNIQUE,
  client_id VARCHAR(64) NOT NULL REFERENCES oauth_clients(client_id) ON DELETE CASCADE,
  redirect_uri TEXT NOT NULL,
  state TEXT,
  scope VARCHAR(200),
  resource TEXT,
  code_challenge VARCHAR(128) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE oauth_authorization_codes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code_hash VARCHAR(64) NOT NULL UNIQUE,
  family_id UUID,
  client_id VARCHAR(64) NOT NULL REFERENCES oauth_clients(client_id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  redirect_uri TEXT NOT NULL,
  code_challenge VARCHAR(128) NOT NULL,
  scope VARCHAR(200),
  resource TEXT,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_oauth_codes_family ON oauth_authorization_codes(family_id);

CREATE TABLE oauth_refresh_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  family_id UUID NOT NULL,
  client_id VARCHAR(64) NOT NULL REFERENCES oauth_clients(client_id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  scope VARCHAR(200),
  resource TEXT,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_oauth_refresh_family ON oauth_refresh_tokens(family_id);
CREATE INDEX idx_oauth_refresh_user_tenant ON oauth_refresh_tokens(tenant_id, user_id);

ALTER TABLE mcp_tokens ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'PAT';
ALTER TABLE mcp_tokens ADD COLUMN oauth_client_id VARCHAR(64);
ALTER TABLE mcp_tokens ADD COLUMN oauth_family_id UUID;
CREATE INDEX idx_mcp_tokens_family ON mcp_tokens(oauth_family_id);
