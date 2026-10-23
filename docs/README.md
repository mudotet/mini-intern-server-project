# Shared documentation

Project-specific documentation lives with its project:

- [Game server](../server/docs/README.md): architecture, API, Battle Pass, testing, deployment, and server workflow.
- [Proto Studio](../tools/docs/README.md): setup, behavior, design specifications, and tools workflow.

Shared references:

- [Context map](../CONTEXT-MAP.md): project glossaries and their relationship.
- [Agent coordination](../.codex/TEAM.md): selecting and coordinating the two teams.
- [Schema revision decision](adr/0001-proto-schema-versions-by-commit.md).
- [Issue tracker](agents/issue-tracker.md), [triage labels](agents/triage-labels.md), and [domain documentation policy](agents/domain.md).

Keep operational documentation in each project's `docs/`; put historical implementation plans under `docs/plans/` and design specifications under `docs/specs/`. Root `docs/adr/` records decisions shared by both projects.
