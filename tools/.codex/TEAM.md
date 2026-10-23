# Tools team

Follow [shared coordination](../../.codex/TEAM.md) and [tools instructions](../AGENTS.md). All paths in assignments are repository-relative; run npm commands from `tools/`.

| Agent | Ownership |
| --- | --- |
| `tools_coder` | Assigned React UI, local Node API, and relevant tests; complete one user-visible behavior at a time |
| `tools_tester` | Assigned Node/browser tests, reproduction, and browser evidence; application source remains coder-owned |
| `tools_reviewer` | Read-only review of UI behavior, accessibility, schema validation, import/export safety, persistence, and verification evidence |

Main reads [README.md](../README.md) for current behavior and prerequisites. Split UI/API changes by owned files and agree on payloads before parallel work. Use [design skills](../.agents/skills/) for relevant UI tasks. Historical [plans and specifications](../docs/README.md) provide design context; current source and README describe delivered behavior.

Agent definitions live in [agents/](agents/); root registry symlinks expose the same files to root sessions. Node tests, production builds, and browser tests provide different evidence; report the checks actually run.
