# Agent Team

## Start

Open a new Codex session from this repository and trust the project when prompted so `.codex/config.toml` loads.
Example: `codex "Use the project team to implement and verify <task>."`
The primary session is the main coordinator; custom agents are spawned on demand within the session.
These files do not launch persistent background workers.
The default model for main and teammates is `gpt-6.1-sol` with `medium` reasoning, as requested by the repository owner.
Role files inherit the selected model and reasoning so main can explicitly override them for a particular task when its complexity warrants a change.
State the reason for task-specific overrides and report unavailable models instead of silently switching them.

## Roles

| Agent | Responsibility | Guide |
| --- | --- | --- |
| Main | Own scope, split tasks, assign files, settle decisions, integrate and report | `docs/agents/pm.md` |
| `coder` | Implement one complete handler/service/DAO flow and relevant tests | `server/docs/agents/coder.md` |
| `tester` | Reproduce failures and verify behavior; edit only assigned test files | `server/docs/agents/tester.md` |
| `reviewer` | Inspect correctness, security, contracts, and evidence without editing | `server/docs/agents/coder.md`, `server/docs/agents/tester.md` |

## Coordination Rules

1. Main reads `AGENTS.md`, `CONTEXT.md`, `server/docs/ROADMAP.md`, and Git status before assigning work.
2. Main delegates meaningful independent work to the project agents; keep trivial or dependent steps inline.
3. Each assignment includes a task ID, objective, allowed files, dependencies, acceptance checks, and known teammate IDs.
4. Main shares newly spawned teammate IDs with the other agents so they can address each other.
5. Assign one writer per file, including tests, fixtures, configuration, and generated artifacts.
6. Teammates may discuss facts and interface contracts directly; only main changes scope, ownership, or priorities.
7. Teammates do not spawn more agents; main owns team lifecycle and the three-thread limit.
8. Before bug fixes, reproduce through the real HTTP flow where possible; report prerequisites that prevent reproduction.
9. Main waits for required dependencies and verification before reporting completion.

## Communication

Use the runtime's native agent messaging tools with actual agent IDs or supported names.
Send messages as `[TASK-ID] To: <agent>; Type: question|contract|blocked|result; Evidence: <paths or command results>; Need: <specific action>`.
Notify main of decisions, blockers, and changes that affect another task.
If direct peer messaging is unavailable, send the message to main for relay and have main deliver the reply.
Main uses a supported follow-up/resume operation for an idle teammate; queuing a message alone may not start a new turn.
Use bounded waits and report missing replies instead of waiting indefinitely.
Do not invent messaging tools, use email/Slack, or treat shared file edits as reliable message delivery.
Return a handoff containing changed files, verification commands and results, unresolved questions, and remaining risks.

## Boundaries and Verification

Preserve user changes and generated files; edit source `.proto` files and regenerate with Gradle.
Keep secrets out of messages and task files; use the session's existing authorization for sensitive operations.
Use focused checks such as `./gradlew compileJava test` and, with Redis/DynamoDB Local available, `./gradlew integrationTest`.
Separate local verification from AWS verification and state exactly which environment was tested.
Role restrictions are instructions; agents inherit the parent session's sandbox and approvals.

## Configuration Checks

Run `codex --strict-config doctor --summary --ascii` to check the installed client's configuration.
Use `/subagents` in the CLI to inspect spawned threads.
For a communication smoke test, ask main to spawn coder and tester, exchange a `PING`/`PONG` through native messaging, and report the route and replies without editing files.
Configuration parsing alone does not prove model access or successful agent communication.
On 2026-10-21, CLI 0.163.0 accepted this configuration and started coder/tester with the requested model.
The child agents reported no direct peer-messaging tools; the relay smoke test was interrupted before a complete round trip was confirmed.
Use main as the communication relay on this runtime and do not claim direct messaging has been verified.

References: [Custom agents](https://developers.openai.com/codex/multi-agent), [project configuration](https://developers.openai.com/codex/config-basic).
