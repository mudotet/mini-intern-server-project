# Shared team coordination

The primary session coordinates work; custom agents run on demand. Root `.codex/agents/` entries are symlinks to project-owned definitions, so a repository-root session can discover both teams without duplicating their prompts.

| Scope | Agents | Team guide |
| --- | --- | --- |
| Java game API | `server_coder`, `server_tester`, `server_reviewer` | [Server team](../server/.codex/TEAM.md) |
| React / Node Proto Studio | `tools_coder`, `tools_tester`, `tools_reviewer` | [Tools team](../tools/.codex/TEAM.md) |

## Coordination

1. Read root and project `AGENTS.md`, relevant source, and Git status. Select the matching team; split cross-project work into explicit assignments.
2. Delegate when requested or required by applicable instructions. Keep trivial and dependent steps inline. Do not spawn another coordinator.
3. Each assignment names the objective, owned files, dependencies, acceptance checks, and known teammate IDs. Assign one writer per file; agents must preserve others' edits.
4. Teammates can discuss contracts through native messaging. Main settles scope and ownership, relays messages when peer messaging is unavailable, and resumes idle agents through supported runtime operations.
5. Teammates do not spawn more agents. Main respects the configured concurrency limit and waits for required handoffs and verification.
6. Handoffs include changed files, commands and observed results, blockers, and remaining risks. Parsing configuration does not prove successful model access or agent communication.

Models and concurrency are shared in [config.toml](config.toml). Defaults remain `gpt-6.1-sol` with `medium` reasoning. Explain explicit overrides; report unavailable models. Agents inherit parent permissions; role instructions do not create a security sandbox.

Start a new trusted session after changing team definitions. Check configuration with `codex --strict-config doctor --summary --ascii` from the root and project directories; inspect spawned threads with `/subagents` in the CLI.

Reference: [Codex custom agents](https://developers.openai.com/codex/multi-agent).
