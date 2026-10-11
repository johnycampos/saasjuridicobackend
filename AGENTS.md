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
