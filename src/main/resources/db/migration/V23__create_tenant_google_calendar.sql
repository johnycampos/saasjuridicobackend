CREATE TABLE tenant_google_calendar (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    connected_email VARCHAR(255) NOT NULL,
    calendar_id     VARCHAR(500) NOT NULL DEFAULT 'primary',
    access_token    VARCHAR(2000) NOT NULL,
    refresh_token   VARCHAR(1000) NOT NULL,
    token_expiry    TIMESTAMPTZ NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    connected_by    UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_tgc_tenant ON tenant_google_calendar(tenant_id);
CREATE INDEX idx_tgc_status ON tenant_google_calendar(status);
CREATE INDEX idx_tgc_token_expiry ON tenant_google_calendar(token_expiry) WHERE status = 'ACTIVE';
