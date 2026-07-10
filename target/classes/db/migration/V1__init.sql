-- ============================================
-- USERS (global, independente de tenant)
-- ============================================
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    nome            VARCHAR(255) NOT NULL,
    avatar_url      VARCHAR(500),
    google_sub      VARCHAR(255) UNIQUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================
-- TENANTS (escritorios)
-- ============================================
CREATE TABLE tenants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) UNIQUE NOT NULL,
    cnpj            VARCHAR(18),
    telefone        VARCHAR(20),
    endereco        TEXT,
    logo_url        VARCHAR(500),
    plano           VARCHAR(50) DEFAULT 'FREE',
    max_membros     INT DEFAULT 5,
    ativo           BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================
-- VINCULO USUARIO <-> TENANT
-- ============================================
CREATE TYPE tenant_role AS ENUM ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER');

CREATE TABLE tenant_members (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            tenant_role NOT NULL DEFAULT 'MEMBER',
    ativo           BOOLEAN DEFAULT TRUE,
    joined_at       TIMESTAMPTZ DEFAULT NOW(),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(tenant_id, user_id)
);

CREATE INDEX idx_tenant_members_tenant ON tenant_members(tenant_id);
CREATE INDEX idx_tenant_members_user ON tenant_members(user_id);

-- ============================================
-- GRUPOS
-- ============================================
CREATE TABLE groups (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    nome            VARCHAR(255) NOT NULL,
    descricao       TEXT,
    cor             VARCHAR(7) DEFAULT '#3B82F6',
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_groups_tenant ON groups(tenant_id);

-- ============================================
-- MEMBROS DO GRUPO
-- ============================================
CREATE TYPE group_role AS ENUM ('LEADER', 'MEMBER');

CREATE TABLE group_members (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            group_role DEFAULT 'MEMBER',
    added_at        TIMESTAMPTZ DEFAULT NOW(),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(group_id, user_id)
);

CREATE INDEX idx_group_members_group ON group_members(group_id);

-- ============================================
-- COLUNAS DO KANBAN
-- ============================================
CREATE TABLE board_columns (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    group_id        UUID REFERENCES groups(id) ON DELETE CASCADE,
    nome            VARCHAR(100) NOT NULL,
    posicao         INT NOT NULL DEFAULT 0,
    cor             VARCHAR(7) DEFAULT '#6B7280',
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_board_columns_group ON board_columns(group_id);
CREATE INDEX idx_board_columns_tenant ON board_columns(tenant_id);

-- ============================================
-- PROCESSOS JURIDICOS
-- ============================================
CREATE TYPE prioridade_tipo AS ENUM ('BAIXA', 'MEDIA', 'ALTA', 'URGENTE');
CREATE TYPE processo_status AS ENUM ('ATIVO', 'ARQUIVADO', 'CANCELADO');

CREATE TABLE processos (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    group_id            UUID REFERENCES groups(id) ON DELETE SET NULL,
    column_id           UUID REFERENCES board_columns(id) ON DELETE SET NULL,
    numero_processo     VARCHAR(50),
    titulo              VARCHAR(500) NOT NULL,
    descricao           TEXT,
    tipo_acao           VARCHAR(255),
    vara                VARCHAR(255),
    comarca             VARCHAR(255),
    tribunal            VARCHAR(100),
    autor               VARCHAR(500),
    reu                 VARCHAR(500),
    prioridade          prioridade_tipo DEFAULT 'MEDIA',
    status              processo_status DEFAULT 'ATIVO',
    valor_causa         DECIMAL(15,2),
    data_distribuicao   DATE,
    prazo_proximo       TIMESTAMPTZ,
    posicao_coluna      INT DEFAULT 0,
    created_by          UUID REFERENCES users(id),
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    updated_at          TIMESTAMPTZ DEFAULT NOW(),
    campos_extras       JSONB DEFAULT '{}'::JSONB
);

CREATE INDEX idx_processos_tenant ON processos(tenant_id);
CREATE INDEX idx_processos_group ON processos(group_id);
CREATE INDEX idx_processos_column ON processos(column_id);
CREATE INDEX idx_processos_numero ON processos(numero_processo);
CREATE INDEX idx_processos_prazo ON processos(prazo_proximo);
CREATE INDEX idx_processos_status ON processos(status);
CREATE INDEX idx_processos_search ON processos
    USING GIN (to_tsvector('portuguese',
        coalesce(titulo,'') || ' ' ||
        coalesce(descricao,'') || ' ' ||
        coalesce(autor,'') || ' ' ||
        coalesce(reu,'')
    ));

-- ============================================
-- RESPONSAVEIS POR PROCESSO
-- ============================================
CREATE TABLE processo_responsaveis (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    processo_id     UUID NOT NULL REFERENCES processos(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    atribuido_em    TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(processo_id, user_id)
);

-- ============================================
-- HISTORICO DE MOVIMENTACOES
-- ============================================
CREATE TABLE processo_historico (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    processo_id         UUID NOT NULL REFERENCES processos(id) ON DELETE CASCADE,
    user_id             UUID REFERENCES users(id),
    tipo                VARCHAR(50) NOT NULL,
    descricao           TEXT NOT NULL,
    dados_anteriores    JSONB,
    dados_novos         JSONB,
    created_at          TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_historico_processo ON processo_historico(processo_id);
CREATE INDEX idx_historico_data ON processo_historico(created_at DESC);

-- ============================================
-- COMENTARIOS
-- ============================================
CREATE TABLE processo_comentarios (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    processo_id     UUID NOT NULL REFERENCES processos(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id),
    conteudo        TEXT NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================
-- ANEXOS
-- ============================================
CREATE TABLE processo_anexos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    processo_id     UUID NOT NULL REFERENCES processos(id) ON DELETE CASCADE,
    user_id         UUID REFERENCES users(id),
    nome_arquivo    VARCHAR(500) NOT NULL,
    tipo_arquivo    VARCHAR(100),
    tamanho_bytes   BIGINT,
    storage_path    VARCHAR(1000) NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================
-- CONVITES
-- ============================================
CREATE TYPE invite_status AS ENUM ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED');

CREATE TABLE invites (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    processo_id     UUID REFERENCES processos(id) ON DELETE CASCADE,
    group_id        UUID REFERENCES groups(id) ON DELETE CASCADE,
    email           VARCHAR(255) NOT NULL,
    tipo            VARCHAR(20) NOT NULL,
    role            VARCHAR(20) DEFAULT 'VIEWER',
    token           VARCHAR(255) UNIQUE NOT NULL,
    status          invite_status DEFAULT 'PENDING',
    invited_by      UUID REFERENCES users(id),
    expires_at      TIMESTAMPTZ NOT NULL,
    accepted_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_invites_email ON invites(email);
CREATE INDEX idx_invites_token ON invites(token);

-- ============================================
-- NOTIFICACOES
-- ============================================
CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    tipo            VARCHAR(50) NOT NULL,
    titulo          VARCHAR(500) NOT NULL,
    conteudo        TEXT,
    link            VARCHAR(500),
    lida            BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_notifications_user ON notifications(user_id, lida, created_at DESC);
