# Roadmap

Milestones are ordered. Later infrastructure is introduced only when the behavior and adapter that need it are implemented.

## 1. Protobuf and Documentation

**Goal:** Establish shared contracts, vocabulary, architecture boundaries, and delivery guidance.

**Scope:** Slice 1 Protobuf schemas and the documentation set for domain, architecture, API, Protobuf, testing, deployment, AWS future scope, agent workflow, and roadmap.

**Dependencies:** None.

**Acceptance criteria:**

- Slice 1 contracts cover init, login, and player resource initialization.
- Schemas follow the documented source layout and Java namespace.
- Documentation is internally consistent and clearly distinguishes current from future scope.
- Generated Protobuf classes are defined as separate from domain models.

## 2. Domain Records and In-Memory Stores

**Goal:** Implement technology-independent Slice 1 state and replaceable persistence ports.

**Scope:** Domain records and in-memory implementations of `AccountStore`, `DeviceStore`, `PlayerStore`, `SessionStore`, and `IdempotencyStore`.

**Dependencies:** Milestone 1.

**Acceptance criteria:**

- Domain records use accepted vocabulary and import no Akka, generated Protobuf, Redis, or AWS types.
- Stores implement the required Slice 1 access patterns behind interfaces.
- In-memory behavior is deterministic and covered by tests.
- No external infrastructure dependency is added.

## 3. Init

**Goal:** Expose public server and configuration metadata before login.

**Scope:** `POST /api/001003`, including mapping, service behavior, response contract, and tests.

**Dependencies:** Milestones 1–2.

**Acceptance criteria:**

- The endpoint requires no JWT.
- The response contains all documented metadata fields.
- The operation creates no Account, Player, Device, or Session.
- Unit and API acceptance tests pass.

## 4. Login

**Goal:** Provide safe identity creation or recovery and authenticated Session issuance.

**Scope:** `POST /api/001001`, Account/Player/Device creation, existing-Device recovery, Account validation, RequestId idempotency, JWT issuance, and tests.

**Dependencies:** Milestones 1–3.

**Acceptance criteria:**

- New Devices create exactly one Account, Player, and Device.
- Existing Devices recover the same Account and Player.
- Supplied Account IDs are validated.
- Repeated RequestIds return the original result, including under concurrent handling.
- New RequestIds create or refresh Sessions.
- JWT claims and response fields match the API contract.

## 5. Init-Resource

**Goal:** Return initial state for the authenticated Player.

**Scope:** `POST /api/002007`, JWT and Session validation, Player profile, Resources, InventoryItems, version fields, and tests.

**Dependencies:** Milestones 1–4.

**Acceptance criteria:**

- Invalid and expired JWTs return stable ApiErrors.
- Player identity is resolved only from validated authentication.
- The response contains documented state and version fields.
- The operation does not mutate gameplay state.
- Cross-Player access through request data is impossible in tests.

## 6. Static Shop

**Goal:** Return shared purchasable ShopOffer configuration.

**Scope:** Static shop contract, configuration model, read service, endpoint, and tests. This is future functionality.

**Dependencies:** Milestones 1–5.

**Acceptance criteria:**

- ShopOffer remains shared configuration rather than Player-owned state.
- Contract versioning and mapping boundaries are followed.
- Reads are deterministic for a selected configuration version.
- No daily resolution or purchase mutation is included.

## 7. Daily Shop

**Goal:** Resolve a Player-specific ShopSnapshot for a shop cycle.

**Scope:** Cycle definition, deterministic offer resolution, Player-specific snapshot storage boundary, endpoint, and tests. This is future functionality.

**Dependencies:** Milestone 6.

**Acceptance criteria:**

- Each ShopSnapshot belongs to one Player and one cycle.
- Repeated reads in the same cycle return a consistent snapshot.
- Cycle transitions are covered by deterministic clock tests.
- Shared ShopOffer configuration is not mutated.

## 8. Purchase Atomicity

**Goal:** Apply a Purchase exactly once without partial Player-state changes.

**Scope:** Purchase contract, RequestId idempotency, validation, atomic Resource and InventoryItem changes, and tests. This is future functionality.

**Dependencies:** Milestones 6–7.

**Acceptance criteria:**

- Insufficient or invalid purchases change no Player state.
- Successful purchases update Resources and InventoryItems atomically.
- Repeated RequestIds return the original Purchase result.
- Concurrent purchase tests prevent double spending and duplicate grants.

## 9. Redis Adapter

**Goal:** Replace process-local Session and idempotency storage where distributed behavior is required.

**Scope:** Redis client selection, `SessionStore` and `IdempotencyStore` adapters, configuration, migration tests, and local development support. This is future infrastructure.

**Dependencies:** Milestones 4 and 8.

**Acceptance criteria:**

- Application services and endpoint contracts remain unchanged.
- Expiration behavior is preserved.
- Adapter integration and failure behavior are tested.
- Redis dependencies are introduced only in this milestone.

## 10. DynamoDB Adapter

**Goal:** Provide durable Account, Device, and Player persistence.

**Scope:** DynamoDB implementations of relevant store ports, access-pattern-driven schema, conditional writes, migration tests, and local development support. This is future infrastructure.

**Dependencies:** Milestones 2 and 8.

**Acceptance criteria:**

- Services and endpoints remain unchanged.
- Identity uniqueness and Purchase atomicity use appropriate conditional or transactional operations.
- Required access patterns are tested against DynamoDB Local or an approved equivalent.
- DynamoDB dependencies are introduced only with the adapters.

## 11. SQS Worker and DLQ

**Goal:** Process asynchronous Purchase events reliably.

**Scope:** Event contract, SQS publisher, idempotent worker, retry policy, DLQ, monitoring expectations, and tests. This is future infrastructure and processing.

**Dependencies:** Milestones 8–10.

**Acceptance criteria:**

- Purchase success publishes the defined event without changing synchronous atomicity.
- Worker retries are safe and idempotent.
- Poison messages reach the DLQ after the configured retry limit.
- Event and worker behavior are covered by integration tests.

## 12. AWS Development Deployment

**Goal:** Deploy a cost-controlled development environment on AWS.

**Scope:** Infrastructure as code, application and worker deployment, DynamoDB, SQS/DLQ, configuration and secret services, health monitoring, budgets, and teardown. This is future scope.

**Dependencies:** Milestones 9–11.

**Acceptance criteria:**

- Infrastructure is reproducible and limited to one selected region.
- IAM follows least privilege and secrets are not stored in source control.
- AWS Budgets and billing alerts exist before sustained operation.
- Health checks, logs, release versioning, rollback, and cleanup are verified.
- Teardown removes resources that can continue incurring cost.
