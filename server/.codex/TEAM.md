# Server team

Follow [shared coordination](../../.codex/TEAM.md) and [server instructions](../AGENTS.md). All paths in assignments are repository-relative; run build commands from `server/`.

| Agent | Ownership and guidance |
| --- | --- |
| `server_coder` | One complete handler → service → DAO flow, contracts, and assigned regression tests; [coder guide](../docs/agents/coder.md) |
| `server_tester` | Reproduction and assigned tests, including real Redis/DynamoDB Local HTTP checks; [tester guide](../docs/agents/tester.md) |
| `server_reviewer` | Read-only review of identity, authorization, transactions, retries, Protobuf compatibility, and verification evidence |

Main uses the [planning guide](../docs/agents/pm.md); deployment tasks use the [deployer guide](../docs/agents/deployer.md). The [workflow](../docs/AGENT_WORKFLOW.md) describes flow delivery; the [roadmap](../docs/ROADMAP.md) owns scope and dated verification results.

Agent definitions live in [agents/](agents/); root registry symlinks expose the same files to root sessions. Changes to the shared proto repository require an explicit handoff to affected consumers. Distinguish unit tests, local integration, Docker checks, and live AWS evidence.
