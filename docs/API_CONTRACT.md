# API contract

Game flows use HTTP POST with Content-Type: application/x-protobuf or application/json. Responses use the same format as requests. X-Request-Id is echoed; Login/Purchase/Battle Pass mutations require 16–128 letters/digits/underscores/hyphens, preferably a random UUID. Information APIs use GET and always return JSON.

## Swagger and JSON

GET /swagger/ serves Swagger UI; GET /swagger/openapi.json serves OpenAPI 3.0.3. Select application/json to use Try it out; copy access_token from Login into Authorize. Swagger/assets run with the app using classpath files.

JSON is parsed with Protobuf JsonFormat, then passed to the existing handler/service. Response fields retain snake_case; int64 values are strings and default 0/false values are printed. Empty requests can use {} or an empty body. JSON fields absent from the schema are rejected. [JsonFormat docs](https://protobuf.dev/reference/java/api-docs/com/google/protobuf/util/JsonFormat.html).

Keep the same RequestId/input when retrying, including when switching JSON ↔ Protobuf transports. Receipts store Protobuf; the same Player still purchases only once. Create a new RequestId for each new operation. The API chooses the response format from the request Content-Type; Accept does not independently change it.

| Flow | Path | Request | Auth |
| --- | --- | --- | --- |
| Public init | /api/001003 | CommonInitRequest | Public |
| Login | /api/001002 | CommonLoginRequestProto | Public |
| Player Init | /api/002007 | Empty PlayerInitRequestProto | Bearer JWT + Redis |
| Static | /api/003004 | Empty body | Bearer JWT + Redis |
| Daily | /api/003005 | Empty body | Bearer JWT + Redis |
| Purchase | /api/003002 | ShopPurchaseRequestProto | Bearer JWT + Redis |

## Information for display

The Swagger **05 · Information** group contains GET APIs that always return application/json and Cache-Control: no-store. They require Bearer JWT + Redis Session, but no Content-Type, body, or X-Request-Id. Nonempty bodies are rejected (400). Player is obtained from the authenticated JWT; a player_id query cannot select another Player.

| Path | Response | Purpose |
| --- | --- | --- |
| /api/info/player | PlayerProfileResponseProto | Display account_id/player_id |
| /api/info/resources | PlayerResourcesResponseProto | Current Resources; read again after Purchase |
| /api/info/packages | ShopPackagesResponseProto | Shared Packages: base prices, rewards, daily quantity ranges |
| /api/info/session | SessionInfoResponseProto | Session Account/Player, expires_at/server_time/remaining_seconds |

Player/Resource use consistent DynamoDB GetItem reads; Packages come from the ShopCatalog used by ShopService. These APIs do not refill Resources, randomize DailyOffers, or write Player. Packages do not contain the selected DailyOffer terms; call Daily for the day's price/discount/reward/counter.

Session info checks JWT signature/expiry and Redis Session; it does not return access_token, device_id, or session_id. expires_at/server_time are Unix seconds; remaining_seconds counts down to JWT expiry. If Redis loses the Session or a new login replaces it, the API returns 401 even if the old JWT has not expired. int64 values remain strings in JSON.

## Login and Init

Login with an empty device_id: the server generates a stable Device ID from RequestId, creates Account/Player, and grants default Resources. Retries must keep both body and RequestId unchanged. Do not change the body to the newly returned Device ID when retrying the first request.

Known Device: send device_id; account_id/player_id are optional but must match if supplied. Use a new RequestId to create a new Session. The response includes account_id, player_id, device_id, access_token, session_expires_at, and new_account.

The login cache lasts up to 12 hours in Redis. Retries retain the original Session; if it is replaced or Redis loses it, use a new RequestId to log in. Identity/InitialResources are still created only once.

This is guest login for the demo: anyone who knows a Device ID can recover its Account. There is no password, OAuth, or multi-Device linking. Treat Device ID as a login credential.

JWT contains account_id/player_id/device_id/session_id and exp. The server checks signature/expiry and compares it with Redis Session before calling the service. Player ID for APIs comes from JWT, not the body.

Public init returns metadata/version/time. Player Init returns current Resources; it does not grant them again.

## Shop and Purchase

Static offer ID: static:<package_id>. Daily: daily:<cycle_start>:<slot_id>:<package_id>, scoped to the Player determined by authentication.

Daily is active when cycle_start <= server time < expires_at. It selects three distinct packages, a 10/20/30% discount, and quantities within configured ranges; price = max(1, base × (100 − discount) / 100), using integer division. Price/reward/discount/limit are stored and retained for the day. Day boundary: 00:00 Asia/Ho_Chi_Minh.

The Purchase request's id is taken from the shop response, with amount=1. The server resolves price/reward itself; it does not accept client prices. Response items are the rewards just received, resources are the post-purchase Resources, and offer_id is the purchased ID.

The same (Player, RequestId, offer ID, amount) returns the stored original response, even if the current balance has changed or the offer has expired. The same RequestId with different input returns 409. Receipts do not expire in this demo.

## Battle Pass

All four endpoints use POST with the existing JSON/Protobuf transport and authenticated Player.
The Swagger **06 · Battle Pass** group documents their full request and response schemas.

| Flow | Path | Request |
| --- | --- | --- |
| State | /api/004001 | Empty body |
| Claim milestone | /api/004002 | pass_id, node_id |
| Unlock Premium | /api/004003 | pass_id |
| Claim quest | /api/004004 | pass_id, quest_id |

The pass is shared across Players and runs from Monday 00:00 to the following Monday in Asia/Ho_Chi_Minh.
State includes the active pass ID, server timestamps, cumulative EXPBP, Premium ownership, six Free/Premium milestone pairs, and daily quest progress.
Thresholds are 100, 200, 300, 400, 500, and 600 EXPBP.
Premium costs 50 Gems once for the active season.
The server resolves all prices, rewards, eligibility, and quest progress.

Successful Static or Daily Shop purchases advance the daily purchase counter and purchased-XP counter in the same transaction as payment and the Shop receipt.
First Trade requires one purchase and grants 100 EXPBP.
Seasoned Trader requires three purchases and grants 150 EXPBP.
Arcane Studies requires receiving 200 XP through Shop purchases and grants 100 EXPBP.
Quests must be claimed manually.
Quest claims add cumulative EXPBP, which is separate from Player XP and is retained when claiming milestones.
Milestone rewards and Premium purchases do not advance Shop quests.

Daily quest counters and claims reset at 00:00 Asia/Ho_Chi_Minh.
A new weekly season resets EXPBP, Premium ownership, and claimed nodes while keeping Player Resources.
Initialization and rollover occur lazily on requests using optimistic Player version checks.
Existing Players with no Battle Pass data start at zero EXPBP.

Each mutation requires X-Request-Id and returns pass, items, and resources.
Quest items contain the awarded expbp, while resources remains the Player's Gold, Gems, and XP.
Premium returns an empty items list after deducting Gems.
All three mutation types share a receipt namespace scoped to Player and RequestId, binding the action, pass ID, and node or quest ID.
The Player update and receipt are atomic.
Identical retries return the original response even after a day or season boundary, and changed input returns REQUEST_ID_REUSED.
Read current State and Resources after replaying an old receipt to display current values.

## Errors

BusinessErrorProto contains code and message. Responses contain no exception/stack trace.

| HTTP | Code |
| --- | --- |
| 400 | INVALID_REQUEST, INVALID_JSON, INVALID_PROTOBUF, INVALID_IDENTITY, REQUEST_ID_REQUIRED, INVALID_PURCHASE |
| 401 | AUTH_INVALID |
| 408 | INVALID_REQUEST (request body timeout) |
| 413 | INVALID_REQUEST (request body exceeds 64 KiB) |
| 404 | OFFER_NOT_FOUND, NODE_NOT_FOUND, QUEST_NOT_FOUND, ROUTE_NOT_FOUND |
| 409 | IDENTITY_MISMATCH, REQUEST_ID_REUSED, INSUFFICIENT_FUNDS, OFFER_EXPIRED, PURCHASE_LIMIT, PREMIUM_REQUIRED, INSUFFICIENT_EXPBP, ALREADY_CLAIMED, ALREADY_PREMIUM, PASS_EXPIRED, QUEST_EXPIRED, QUEST_INCOMPLETE, RESOURCE_CAPACITY, RETRY |
| 500 | INTERNAL_ERROR |

RETRY: resend the same body/RequestId.
Unknown routes return 404.
See the [schemas](../server/src/main/proto) and [demo script](../scripts/demo.sh) for encoding/decoding.
