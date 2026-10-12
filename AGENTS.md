## graphify & archify

### Graphify (Knowledge Graph)
This project has a knowledge graph at `graphify-out/` with god nodes, community structure, and cross-file relationships for this backend codebase (Java 21 / Spring Boot 3.x).

Rules:
- For codebase questions, first run `graphify query "<question>"` when `graphify-out/graph.json` exists. Use `graphify path "<A>" "<B>"` for relationships between two symbols and `graphify explain "<concept>"` for a focused explanation. These return a scoped subgraph, usually much smaller than `GRAPH_REPORT.md` or raw grep output.
- If `graphify-out/wiki/index.md` exists, use it for broad navigation instead of raw source browsing.
- Read `graphify-out/GRAPH_REPORT.md` only for broad architecture review, or when `query`/`path`/`explain` do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

### Archify (Visual Architecture Diagrams)
Visual architecture diagrams are maintained in `../docs/architecture/`.
- Backend Architecture: [backend-architecture.html](file:///D:/desenvolvimento/sasjuridico/docs/architecture/backend-architecture.html) (from `backend-architecture.architecture.json`)
- System Overview: [system-overview.html](file:///D:/desenvolvimento/sasjuridico/docs/architecture/system-overview.html)
- Google OAuth Sequence: [google-calendar-oauth.html](file:///D:/desenvolvimento/sasjuridico/docs/architecture/google-calendar-oauth.html)
- MCP + OAuth 2.1 Sequence: [mcp-oauth.html](file:///D:/desenvolvimento/sasjuridico/docs/architecture/mcp-oauth.html) — servidor MCP (docs em ../docs/mcp/README.md, pendencias em ../docs/mcp/PENDENCIAS.md)

Agents can validate and generate diagrams with Archify:
`node <archify-dir>/bin/archify.mjs deliver architecture ../docs/architecture/backend-architecture.architecture.json ../docs/architecture/backend-architecture.html --quality showcase --json`

## Processos arquivados — regra para queries novas
`processos.status` = `ATIVO|ARQUIVADO|CANCELADO` (soft delete; `DELETE /api/processos/{id}` arquiva). **Toda listagem nova de processo — ou de tarefa/parcela/audiência/contrato exibida em board, busca, agenda, dashboard, financeiro ou tool MCP — precisa filtrar `status = ATIVO`** (nas queries de tarefa/parcela/audiência, via subquery em `Processo`). Só `GET /api/processos/arquivados` lista arquivados; `getById` continua devolvendo arquivado (tela de detalhe). Arquivar/desarquivar: `requireMinRole(MEMBER)` **antes** do lock pessimista, depois `requireGroupAccess`. Plano/revisões/E2E em `../docs/arquivados/`.

## Servidor MCP (`modules/mcp`) — regras ao mexer
Docs: `../docs/mcp/README.md` · **Pendências abertas: `../docs/mcp/PENDENCIAS.md` (ler antes, atualizar ao resolver)** · Diagrama: `../docs/architecture/mcp-oauth.html`.
- `POST /mcp` (JSON-RPC) autenticado por `security/McpTokenAuthFilter` (Bearer `jfmcp_`, PAT ou OAuth, ambos em `mcp_tokens`). O filtro popula `SecurityContext` + `TenantContext` a partir do token e limpa no `finally`; revalida membro ativo a cada request. JWT do app não autentica `/mcp`; token MCP não autentica `/api/**`.
- Toda tool roda com tenant+usuário do token e **reaproveita os services do app** (`GroupService.resolveGroupRestriction`). Tool que recebe `processoId` valida a área antes (`ProcessoService.getById`), porque vários services (tarefa, movimento, audiência) só checam tenant.
- **Escritas**: usar `McpWriteService`/`McpTarefaWriteService` — transação com `ProcessoRepository.findByIdAndTenantIdForUpdate` (lock pessimista) e revalidação de área sob o lock. Escrita exige papel ≥ MEMBER; VIEWER só lê. Exclusões exigem `confirmar=true` e `annotations.destructiveHint`.
- **OAuth** (`modules/mcp/oauth`): todo estado no banco (Cloud Run multi-instância). Rotação/revogação serializadas por `OAuthRefreshTokenRepository.lockFamily` (`pg_advisory_xact_lock`). `handleToken` usa `noRollbackFor = OAuthException` para a revogação ser commitada mesmo devolvendo erro — **não** introduzir `REQUIRES_NEW` aí (deadlock com o advisory lock; pool Hikari = 3).
- Segredos (tokens, codes, client_secret) só como SHA-256; nunca logar token em claro. Respostas não expõem `tenantId`.
- Env: `PUBLIC_BACKEND_URL` (issuer OAuth). Migrations: V27, V28. Testes: `src/test/java/com/jurisflow/modules/mcp/**` + E2E em `../docs/mcp/e2e*/` (Postgres local — nunca o Neon do `.env`).
