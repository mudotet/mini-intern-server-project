# Proto Studio tools

Read [README.md](README.md) for setup and behavior, [CONTEXT.md](CONTEXT.md) for terminology, and [docs/README.md](docs/README.md) for design references.

## Implementation

- This is an independent React / Vite app with a local Node API. Java, Redis, and DynamoDB are not runtime dependencies.
- Work in `src/`, `api/`, and the relevant tests. Keep UI, API validation, and schema import/export behavior consistent.
- GitHub schema loading is read-only and selects a full commit SHA. Editing and ZIP export do not publish schemas or update the server submodule.
- Preserve relative schema paths, imports, and declarations outside the edited elements. Treat repository URLs, schema files, and imported archives as untrusted input.
- Preserve browser drafts, source settings, and theme preferences across UI changes. Check empty, loading, error, and populated states for affected views.
- Design skills live in [.agents/skills/](.agents/skills/). Start a session in `tools/` for automatic discovery; from a root session, read the relevant skill there explicitly when needed.

## Checks and team

- Run commands from `tools/`: `npm ci`, `npm test`, `npm run build`. For changed browser flows, use `npm run test:e2e`; see the README for browser prerequisites.
- Use focused checks appropriate to the change; report browser evidence separately from Node tests and build results.
- For delegated work, read [.codex/TEAM.md](.codex/TEAM.md); use the tools agents rather than the server agents.
