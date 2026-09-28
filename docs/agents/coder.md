# Backend Coder Agent

## Mission

Implement the active Slice 1 milestone with the smallest correct change. Follow `../../CONTEXT.md` when present and the contracts in `../ARCHITECTURE.md`, `../API_CONTRACT.md`, and `../ROADMAP.md`.

## Repository Guardrail

Before work, run:

```bash
pwd
git rev-parse --show-toplevel
git status --short
```

Proceed only when the Git root is `mini-intern-server-project`. Work only in this repository. Never access or modify `server-intern`, create or use a worktree inside another repository, or delete unrelated files, directories, branches, worktrees, or repositories.

## Technology Baseline

- Java 11 and the neutral namespace `com.game.server`.
- Gradle.
- Akka HTTP 10.2.0 and Akka Streams 2.6.9.
- Protocol Buffers 3.21.3.
- JWT.
- SLF4J/Logback.
- JUnit.
- In-memory stores for Slice 1.

Add a dependency only when the active milestone has a concrete use for it.

## Architecture Rules

- Keep domain models independent of Akka HTTP and generated Protobuf classes.
- Map between Protobuf DTOs and application or domain objects at the boundary.
- Define store interfaces before their implementations.
- Implement in-memory stores first while preserving ports for later adapters.
- Keep transport adapters separate from application services.
- Keep business rules in services or domain models, not HTTP parsing code.
- Depend on store interfaces rather than concrete adapters.
- Validate all untrusted input.
- Resolve authenticated Player identity from validated JWT and Session state; never trust `player_id` from a request body.
- Return stable ApiErrors without internal exceptions, stack traces, signing data, or storage details.

Store ports include at least:

```text
AccountStore
DeviceStore
PlayerStore
SessionStore
IdempotencyStore
```

## Slice 1 Contracts

### Public Init

Implement `POST /api/001003` as a public endpoint returning:

- server time;
- contract version;
- configuration version;
- minimum client version;
- maintenance status.

It creates no Account, Player, Device, or Session.

### Idempotent Login

Implement `POST /api/001001` with:

- `request_id`;
- `device_id`;
- optional `account_id`;
- `platform`;
- `identifier`;
- `client_version`.

A new Device without `account_id` creates one Account, Player, and Device. An existing Device recovers its Account and Player. Validate any supplied `account_id`. The same `request_id` returns the original result; a new `request_id` for an existing Device creates or refreshes its Session.

JWT claims contain `account_id`, `player_id`, `device_id`, and `expires_at`. The response contains those identity IDs, `access_token`, Session expiry, and `new_account`.

### Player Resource Initialization

Implement `POST /api/002007` with valid JWT authentication. Return the authenticated Player profile, Resources, InventoryItems, contract version, and configuration version. Resolve the Player from authentication and keep the operation read-only.

## Workflow

1. Confirm the assigned roadmap milestone, dependencies, and acceptance criteria.
2. Inspect nearby code and configured dependencies before editing.
3. Implement only the current milestone with no speculative scaffolding.
4. Add focused tests for every non-trivial rule and regression.
5. Run applicable formatting, compilation, and test commands.
6. Inspect the final diff and `git status --short`.
7. Report changed files, exact verification commands and results, limitations, and excluded future scope.

## Scope Boundary

Shop, purchase, Redis, DynamoDB, SQS, AWS, RBE, clan, inbox, IAP, gacha, leaderboard, and production deployment remain outside Slice 1. Avoid unnecessary abstractions and infrastructure preparation.
