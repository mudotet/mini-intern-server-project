# Repository guidance

This repository contains two independent projects. Read the matching instructions before working in either tree:

- `server/`: [server/AGENTS.md](server/AGENTS.md) — Java game API, Gradle, infrastructure, and server documentation.
- `tools/`: [tools/AGENTS.md](tools/AGENTS.md) — React Proto Studio, Node API, UI skills, and tool documentation.
- Work spanning both projects: read both guides and agree on the shared contract before editing.

## Shared working rules

- Check Git status and the relevant source before editing. Preserve meaningful user changes and use the current checkout.
- Keep project-specific source, build files, documentation, and agent guidance inside its project. Root files describe shared concerns only.
- Use [CONTEXT-MAP.md](CONTEXT-MAP.md) to find terminology and [docs/README.md](docs/README.md) to find shared policies.
- For team work, read [.codex/TEAM.md](.codex/TEAM.md), then the project's team guide. Assign one writer per file and report actual checks and remaining risks.
- Keep changes simple and scoped to the requested behavior. Do not create empty scaffolding.
- Be able to explain customer-facing behavior, design choices, malicious inputs, and failure modes at a system level. For explicitly requested PoCs and demos, prioritize speed and scope.
- Keep secrets out of code, logs, and handoffs. Commit, push, provision, or deploy only with user authorization.

## Shared policies

- GitHub issues: [issue tracker](docs/agents/issue-tracker.md).
- Triage labels: [triage labels](docs/agents/triage-labels.md).
- Terminology and decisions: [domain documentation](docs/agents/domain.md).
