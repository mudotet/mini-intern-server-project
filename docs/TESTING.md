# Testing Strategy

## Principles

Tests verify observable Slice 1 behavior at the smallest practical boundary. Domain and application tests avoid Akka and infrastructure dependencies; transport tests verify HTTP, Protobuf, authentication, and ApiError mapping. Tests use deterministic clocks and identity/token generators where expiration or repeatability matters.

## Unit Tests

Unit tests cover pure mapping, validation, domain rules, token claim handling, and service branches with in-memory or test-double ports. They verify outcomes without network or external infrastructure.

Key cases include:

- Required login field validation.
- Supplied Account identity validation.
- New versus existing Device decisions.
- JWT claim construction and expiration decisions.
- ApiError mapping without internal detail leakage.
- Protobuf-to-application and application-to-Protobuf mapping.

## Service Integration Tests

Application services run with the real in-memory implementations of `AccountStore`, `DeviceStore`, `PlayerStore`, `SessionStore`, and `IdempotencyStore`. These tests verify interactions across ports while remaining process-local.

They cover identity creation, existing Device recovery, Session creation or refresh, stored idempotent results, and authenticated Player state reads.

## API Behavior Tests

API tests exercise Akka HTTP routes with encoded requests and decoded responses. They verify paths, methods, authentication requirements, status behavior, Protobuf payloads, and stable ApiError responses. They do not require Redis, DynamoDB, SQS, or AWS.

## Protobuf and Contract Tests

- All schema files compile with Protocol Buffers 3.21.3.
- Generated classes use `com.game.server.proto`.
- Fixtures round-trip without losing defined data.
- App, model, and common schemas respect their dependency boundaries.
- Removed published fields retain reserved names and numbers.
- Endpoint responses expose only documented fields and stable error contracts.

Buf lint and breaking checks are future optional tooling unless configuration is added.

## Acceptance Tests

### Init

- `POST /api/001003` succeeds without JWT.
- Response contains server time, contract version, configuration version, minimum client version, and maintenance status.
- No Account, Player, Device, or Session is created.

### Login

- A new Device without Account ID creates exactly one Account, Player, and Device.
- The response returns their IDs, access token, Session expiration, and `new_account = true`.
- An existing Device recovers the same Account and Player.
- A supplied matching Account ID is accepted.
- A supplied conflicting Account ID returns `ACCOUNT_ID_MISMATCH` and changes no identity state.

### Login Idempotency

- Repeating the same RequestId returns the original login result.
- The retry creates no duplicate Account, Player, Device, or Session result.
- A new RequestId for an existing Device creates or refreshes the Session.
- Concurrent handling of the same RequestId resolves to one stored result.

### JWT and Session

- A valid JWT exposes matching `account_id`, `player_id`, `device_id`, and `expires_at` claims.
- Missing, malformed, tampered, or unknown-session JWTs return `UNAUTHORIZED`.
- Expired JWTs or Sessions return `SESSION_EXPIRED`.
- Tokens and signing secrets never appear in error payloads.

### Player Resource Initialization

- `POST /api/002007` rejects requests without a valid JWT.
- A valid JWT returns only its Player profile, Resources, and InventoryItems plus contract and configuration versions.
- Request-body Player identity cannot select another Player.
- Calling the endpoint does not mutate gameplay state.
- Missing authenticated Player state returns `PLAYER_NOT_FOUND`.

## Negative and Security Tests

- Reject missing or malformed required fields.
- Reject unverified identity conflicts.
- Reject invalid signatures and altered JWT claims.
- Reject expired authentication before Player data access.
- Verify client errors contain no stack traces or infrastructure details.
- Verify public init does not create identity state.
- Verify repeated resource reads leave all stores unchanged.

## Slice 1 Definition of Done

Slice 1 is complete when:

- The three documented endpoints meet their contracts.
- Protobuf schemas compile and generated classes remain outside domain models.
- Unit, service integration, API behavior, contract, acceptance, and negative/security tests pass through the Gradle test workflow.
- Login retries are proven idempotent, including concurrent same-RequestId behavior.
- Authenticated identity comes only from validated JWT and Session state.
- Resource initialization is read-only.
- No Redis, DynamoDB, SQS, AWS, shop, or purchase functionality is required.
- Documentation matches the implemented behavior and all verification results are recorded before completion is claimed.
