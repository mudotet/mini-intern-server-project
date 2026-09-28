# API Contract

## Scope

This document defines the Slice 1 HTTP contracts. Payloads are represented by Protobuf messages. Static shop, daily shop, purchase, and asynchronous purchase event APIs are future scope and are not current functionality.

## Endpoint Summary

| Operation | Method and path | Authentication | Responsibility |
| --- | --- | --- | --- |
| `COMMON_INIT` | `POST /api/001003` | Public; no JWT | Return server and configuration metadata before login |
| `COMMON_LOGIN` | `POST /api/001001` | Public; no JWT | Resolve or create identity and return an authenticated Session |
| `PLAYER_RESOURCE_INIT` | `POST /api/002007` | Valid JWT required | Return the authenticated Player profile, Resources, and InventoryItems |

## COMMON_INIT

### Request

`POST /api/001003` is public. It requires no JWT and has no identity creation responsibility.

### Response

- `server_time`
- `contract_version`
- `configuration_version`
- `minimum_client_version`
- `maintenance`

The endpoint must not create an Account, Player, Device, or Session.

## COMMON_LOGIN

### Request

- `request_id`
- `device_id`
- optional `account_id`
- `platform`
- `identifier`
- `client_version`

This endpoint is public and requires no JWT.

### Identity Behavior

- A new `device_id` with no `account_id` creates an Account, Player, and Device.
- An existing `device_id` recovers its existing Account and Player.
- A supplied `account_id` is validated against stored identity; it is not trusted as authority.
- Identity conflicts fail with a stable ApiError and must not relink or overwrite identity.

### Idempotency

The first completed operation for a `request_id` stores its login result. Repeating the same `request_id` returns that original result and does not create duplicate identity or session state. A new `request_id` for an existing Device creates or refreshes its Session.

### JWT Claims

The access token contains:

- `account_id`
- `player_id`
- `device_id`
- `expires_at`

Token signing and validation must prevent claim tampering. The server validates expiration before allowing authenticated operations.

### Response

- `account_id`
- `player_id`
- `device_id`
- `access_token`
- `session_expires_at`
- `new_account`

`new_account` is true only when this operation created the Account.

## PLAYER_RESOURCE_INIT

### Request and Authentication

`POST /api/002007` requires a valid, unexpired JWT. Player identity is resolved from validated claims. Any `player_id` in request data is ignored or rejected and is never trusted for authorization.

### Response

- player profile
- Resources
- InventoryItems
- `contract_version`
- `configuration_version`

This endpoint reads the authenticated Player's initial state and must not mutate gameplay state.

## Stable API Errors

The concrete wire representation belongs in `game/common/error.proto`. Slice 1 uses stable semantic codes:

| Code | Meaning | Typical HTTP status |
| --- | --- | --- |
| `INVALID_REQUEST` | Required input is missing or malformed | `400` |
| `ACCOUNT_ID_MISMATCH` | Supplied Account does not match the Device identity | `409` |
| `UNAUTHORIZED` | JWT is absent, invalid, or cannot identify a valid Session | `401` |
| `SESSION_EXPIRED` | JWT or Session has expired | `401` |
| `PLAYER_NOT_FOUND` | Authenticated Player state cannot be found | `404` |
| `INTERNAL_ERROR` | An unexpected server failure occurred | `500` |

The exact HTTP mapping is part of the transport contract and should remain consistent once schemas are defined. Error responses contain an ApiError code and client-safe message. They must not expose stack traces, signing data, implementation class names, or storage details. The same failure class returns the same stable code across retries.

## Identity and Security Rules

- Public endpoints must not infer authentication from unverified identifiers.
- `account_id` supplied during login must be validated.
- Authenticated Player identity comes only from a validated JWT and Session.
- Claims must bind Account, Player, and Device consistently.
- Invalid or expired tokens return ApiError and never reach Player data access.
- Resource initialization cannot access another Player through request-body identity.
- Logs must not expose access tokens or signing secrets.

## Future API Scope

Static shop, daily shop, purchase, and asynchronous purchase event contracts will be defined in later milestones. They must not be represented as implemented Slice 1 endpoints. Redis, DynamoDB, and SQS choices do not alter the public Slice 1 contracts.
