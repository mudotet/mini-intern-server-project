# Battle Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking. Implemented and verified in the existing Conductor workspace on 2026-10-23.

**Goal:** Add a ten-node Battle Pass demo with Quest EXP grants, Gems Premium access, one reward per node, persistent Heroes, and Swagger APIs.

**Architecture:** Extend the existing handler → service → GameDao flow with a concrete BattlePassCatalog and BattlePassService. Store Battle Pass progress and Hero ownership in a separate DynamoDB item so existing shop writes cannot erase them; use transactions when changing Player Resources. Compute the active pass from a shared schedule and the injected Clock.

**Tech Stack:** Java 11, Akka HTTP, Protobuf, DynamoDB, existing Redis Sessions, JUnit 5; no new runtime dependencies.

**Spec:** [Battle Pass design](../BATTLE_PASS.md).

## Global Constraints

- Exactly one configured reward per node: one Resource quantity or one Hero.
- Each node requires its own full 500 BattlePassEXP. Ten demo nodes require 5,000 EXP.
- 100 Gems buys PremiumAccess once per pass; no automatic node claims.
- Consecutive 30-day passes repeat the shared template. Resources and Hero ownership persist; pass progress, claims, and PremiumAccess reset.
- Basic/Epic/Legendary duplicates grant 10/50/100 Gems respectively, replacing the Hero reward.
- Quest tracking and a game UI are outside scope. Quest EXP input is accepted only by an explicitly enabled demo endpoint.
- Use existing Session authorization, RequestId conventions, and JSON/Protobuf handling. Preserve earlier repository restructuring and unrelated changes.
- All Gradle commands below run from server/. Never change existing proto field numbers or edit generated Java.
- Schema files live in a separately versioned submodule. Keep schema changes and the parent pointer visible as separate changes; the user's later implement-skill invocation authorizes local commits; no push is authorized.

## Review Focus

- Existing accounts lack Battle Pass state: treat missing state as zero progress and no Heroes, without refilling Resources. Pin in Task 1.
- Concurrent shop and Battle Pass operations: Resources must not be lost or rewarded twice. Pin in Tasks 3–4.
- A delayed request crosses a pass boundary: reject a new mutation for the expired pass; replay committed receipts without another mutation. Pin in Task 2.
- Repeated Quest IDs versus repeated grant IDs: allow legitimate repeat completions but reject changed grant payloads, including through new RequestIds. Pin in Task 2.
- Configuration or server restart changes the apparent schedule: use a stable configured origin and reject malformed configurations before serving requests. Pin in Tasks 1 and 5.

## Configuration and API contract

Add server/src/main/resources/battle_pass.textproto with the ten rewards in BATTLE_PASS.md. Use node IDs node_1 through node_10; Hero IDs hero_scout, hero_knight, hero_dragon, hero_phoenix. The default origin is a fixed, documented timestamp (2026-10-01T00:00:00+07:00), not process startup time. Duration is 2,592,000 seconds. Later configuration changes require deliberate rollout; do not hot-reload rewards during a pass.

| Method/path | Handler | Operation |
| --- | --- | --- |
| GET /api/info/battle-pass | BattlePassHandler | View active definition, dates, progress, nodes, and Hero ownership |
| POST /api/004002 | BattlePassPurchaseHandler | Buy PremiumAccess |
| POST /api/004003 | BattlePassClaimHandler | Claim one reached, eligible node |
| POST /api/004004 | BattlePassQuestGrantHandler | Simulate a trusted Quest reward; demo-only |

Mutation requests carry battle_pass_id and X-Request-Id. Claim adds node_id; Quest grant adds quest_id, grant_id, and positive int64 exp_bp. The Player identity always comes from Session. Register the Quest handler only when DEMO_QUEST_GRANTS_ENABLED=true; default false, enabled explicitly in the local Docker demo. Never enable it in the EC2 deployment template.

Use these Protobuf outer classes/messages in com.game.server.proto:

- game/model/battle_pass.proto → BattlePassModel: BattlePassRewardProto (oneof Resource resource or string hero_id), HeroRarity (UNSPECIFIED, BASIC, EPIC, LEGENDARY), HeroProto, BattlePassProgressProto, PlayerBattlePassStateProto.
- game/blueprint/battle_pass.proto → BattlePassBlueprintContract: BattlePassBlueprint, BattlePassNodeConfig, HeroDefinition, HeroCompensationRule. Nodes contain one BattlePassRewardProto; runtime validation rejects its unset case.
- game/app/battle_pass/battle_pass.proto → BattlePassContract: BattlePassResponseProto, BattlePassNodeProto (definition plus reached/claimed flags).
- game/app/battle_pass/battle_pass_purchase.proto → BattlePassPurchaseContract: BattlePassPurchaseRequestProto and BattlePassPurchaseResponseProto.
- game/app/battle_pass/battle_pass_claim.proto → BattlePassClaimContract: BattlePassClaimRequestProto and BattlePassClaimResponseProto, containing exactly one actual granted reward plus updated state.
- game/app/battle_pass/battle_pass_quest_grant.proto → BattlePassQuestGrantContract: BattlePassQuestGrantRequestProto and BattlePassQuestGrantResponseProto.

Progress stores total_exp_bp, battle_pass_id, premium_owned, and claimed_node_ids. Derive reached_nodes = total_exp_bp / 500 and EXP toward the next node = total_exp_bp % 500. At completion, report completed=true, reached_nodes=10, and no next node. Derived display fields are not separately persisted.

## Task 1: Working Battle Pass view and shared definitions

**Files:** Create the model, blueprint, and view .proto files above; server/src/main/resources/battle_pass.textproto; server/src/main/java/com/game_server/general/service/BattlePassCatalog.java; server/src/main/java/com/game_server/general/service/BattlePassService.java; server/src/main/java/com/game_server/general/app/handler/battlepass/BattlePassHandler.java. Modify GameDao.java, Main.java, and ApiCodes.java in their existing packages. Tests: server/src/test/java/com/game/server/BattlePassCatalogTest.java and BattlePassHttpIntegrationTest.java.

**Interfaces:**
- BattlePassCatalog.load() → BattlePassCatalog.
- BattlePassCatalog(BattlePassBlueprint blueprint); blueprint() → BattlePassBlueprint; node(String nodeId) → BattlePassNodeConfig; hero(String heroId) → HeroDefinition; compensation(HeroRarity rarity) → long.
- BattlePassService(GameDao dao, BattlePassCatalog catalog, Clock clock).
- BattlePassService.view(String playerId) → BattlePassResponseProto.
- GameDao.battlePass(String playerId) → GameDao.BattlePassState. State has final long version and final PlayerBattlePassStateProto data; missing state uses version=-1.

- [x] Write catalog tests asserting 10 nodes, 5,000 EXP cap, exact rewards, and 100-Gem price. Reject unset rewards, unknown Heroes/resources, nonpositive quantities, duplicate IDs, unknown rarity, invalid schedule, and nonpositive duration. Protobuf oneof prevents representing multiple rewards at once; test the active-case behavior rather than assuming repeated fields.
- [x] Run ./gradlew test --tests '*BattlePassCatalogTest' and observe the expected failure before implementation.
- [x] Implement the catalog, view contract, and missing-state read. The Battle Pass state item key is battlepass:<playerId>, with version and serialized PlayerBattlePassStateProto. Do not add fields to GameDao.Player or its existing item serialization.
- [x] Implement stable pass IDs from the template ID and interval index. Before the origin, return BATTLE_PASS_NOT_ACTIVE; at end, select the next interval. A read presents zero current-pass progress after rollover while retaining owned Heroes; it need not write a reset.
- [x] Register the authenticated GET handler using the existing info-handler pattern. Add view HTTP assertions for a fresh account, an existing account without the new item, a future origin, and the exact start/end boundaries.
- [x] Run ./gradlew generateProto compileJava test --tests '*BattlePassCatalogTest'; run the focused HTTP test with local dependencies before declaring the view flow complete.

## Task 2: Working demo Quest EXP grant

**Files:** Create the Quest grant .proto and BattlePassQuestGrantHandler.java alongside Task 1's handler. Modify BattlePassService.java, GameDao.java, Main.java, ApiCodes.java, server/docker/docker-compose.yaml, and server/src/main/resources/swagger/openapi.json. Tests: BattlePassServiceTest.java and BattlePassHttpIntegrationTest.java in server/src/test/java/com/game/server/.

**Interfaces:**
- BattlePassService.grantQuestExp(String playerId, BattlePassQuestGrantRequestProto request, String requestId) → BattlePassQuestGrantResponseProto.
- GameDao.battlePassReceipt(String playerId, String requestId) → GameDao.BattlePassReceipt or null.
- GameDao.questGrant(String playerId, String grantId) → GameDao.BattlePassReceipt or null.
- GameDao.commitBattlePass(GameDao.Player player, GameDao.BattlePassState state, String requestId, GameDao.BattlePassReceipt receipt, String grantId) → boolean; nullable grantId means no Quest receipt.
- GameDao.linkBattlePassReceipt(String playerId, String requestId, GameDao.BattlePassReceipt receipt) → boolean, conditionally recording a replay alias without modifying rewards.
- BattlePassReceipt contains the operation name, canonical input bytes, and response bytes. Canonical input includes the intended pass and all operation-specific fields. Fresh-state commit expects no version; existing-state commit checks its version.

- [x] Write tests for total EXP 499, 500, 510, 1,000, 400+110=510, one large multi-node grant, and a positive grant above remaining capacity capped at 5,000. Reject zero/negative EXP and malformed IDs. Avoid arithmetic overflow by bounding the accepted increment to remaining capacity before addition.
- [x] Run ./gradlew test --tests '*BattlePassServiceTest' and observe failures for the unimplemented grant flow.
- [x] Implement atomic Player version check, Battle Pass state update, operation receipt, and optional Quest grant receipt. Reuse existing Player serialization so daily snapshots and Resources remain intact. Condition conflicts return false for bounded service retries; unrelated storage failures propagate.
- [x] Store operation receipts under battlepass-op:<playerId>:<requestId> and grant receipts under battlepass-grant:<playerId>:<grantId>. Operation identities span all Battle Pass mutation types. A replay with a different RequestId records a conditional alias, so that RequestId cannot later bind to another operation.
- [x] Implement reset-on-mutation of expired pass progress while retaining owned Heroes. Validate intended pass and Clock again for each mutation attempt. Existing committed receipts can replay after expiry; new expired-pass grants fail.
- [x] Register the demo handler behind the explicit flag. Add HTTP tests for absent route when disabled, authentication when enabled, changed grant input conflicts, same Quest/new grant acceptance, same grant/new RequestId deduplication, conflicting aliases, and concurrent identical grants.
- [x] Run the focused service and HTTP tests. Verify the response contains progress and no node reward.

## Task 3: Working Gems Premium purchase

**Files:** Create the purchase .proto and BattlePassPurchaseHandler.java. Modify BattlePassService.java, Main.java, ApiCodes.java, and Swagger. Extend the two Task 2 test files.

**Interfaces:** BattlePassService.purchasePremium(String playerId, BattlePassPurchaseRequestProto request, String requestId) → BattlePassPurchaseResponseProto. Consume Task 2's state and transaction interfaces; grantId is null.

- [x] Write a test using the existing initial 100 Gems: purchase yields zero Gems and premium_owned=true. Test 99 Gems fails without changes, repeat purchase cannot debit again, a changed RequestId payload conflicts, and purchase alone awards no node reward.
- [x] Run the focused service tests and observe the missing purchase behavior fail.
- [x] Implement Session-derived purchase using the same version checks and transaction. Already-owned access returns current ownership without another debit. Bind successful requests to receipts even when they do not charge again.
- [x] Add authenticated handler, request builder, Swagger request example, and response. Test late purchase makes earlier reached Premium nodes eligible without marking them claimed.
- [x] Add real HTTP concurrency tests for two purchase requests, purchase racing a shop mutation, and a purchase at expiration. Assert the combined state matches one valid ordering and no Gems disappear.
- [x] Run ./gradlew test --tests '*BattlePassServiceTest' and the focused HTTP tests.

## Task 4: Working one-reward node claims and Heroes

**Files:** Create the claim .proto and BattlePassClaimHandler.java. Modify BattlePassService.java, Main.java, ApiCodes.java, and Swagger. Extend the existing Battle Pass tests.

**Interfaces:** BattlePassService.claim(String playerId, BattlePassClaimRequestProto request, String requestId) → BattlePassClaimResponseProto. Consume catalog node/Hero lookups and Task 2's commit/receipt methods.

- [x] Write tests proving node_1 rejects 499 EXP and accepts 500; node_2 rejects 999 and accepts 1,000 only with PremiumAccess. Claim Free node_3 while earlier nodes are unclaimed. Assert each successful response contains one actual reward and total EXP is unchanged.
- [x] Run focused service tests and observe missing claim behavior fail.
- [x] Implement lookup → active-pass check → full-node EXP check → Premium check → claimed check → actual reward → one transaction. Mark the node claimed only together with the Resource or Hero change and receipt.
- [x] Persist Hero IDs in PlayerBattlePassStateProto. A duplicate Hero produces one Resource reward with gem quantity 10/50/100 by rarity and leaves Hero ownership unchanged. No Hero and compensation are awarded together. Use Math.addExact for Resource additions and reject overflow without partial changes.
- [x] Add HTTP tests for Basic/Epic/Legendary awards and compensation across rollover, two simultaneous claims for one node, concurrent shop updates, duplicate claims with different RequestIds, receipt replay, and RequestId reuse across purchase/claim/grant operations.
- [x] Run focused service and real-dependency HTTP tests before declaring the claim flow complete.

## Task 5: Swagger rehearsal and regression delivery

**Files:** Modify server/src/main/resources/swagger/openapi.json and swagger/index.html only if needed by navigation; server/docs/API_CONTRACT.md; server/docs/BATTLE_PASS.md; server/docs/TESTING.md; server/docs/ROADMAP.md; server/README.md; server/.env.example. Create server/scripts/battle-pass-demo.sh. Extend BattlePassHttpIntegrationTest.java for restart coverage.

**Interfaces:** Use the four established API actions and existing Login/Session flow. No new business interfaces.

- [x] Add restart-persistence HTTP coverage: reach nodes, buy Premium, claim a Hero, restart handlers/DAO, verify state and receipt replay, then advance Clock and verify reset preserves Resources/Hero ownership. Keep the configured origin stable.
- [x] Document how to enable the demo-only Quest endpoint locally and verify that server/deploy/ec2-compose.yaml does not enable it. Each Swagger mutation example includes pass ID and a fresh RequestId, with grant IDs explained separately.
- [x] Implement a demo script that logs in, reads the active pass, grants 400 then 110 EXP, claims node_1, grants the remaining 4,490 EXP, purchases Premium, and claims later nodes including Heroes. Print rewards/progress without printing credentials.
- [x] Run ./gradlew clean compileJava test integrationTest installDist with isolated Redis/DynamoDB Local available. Preserve other running services and use the existing integration tests' isolated table/namespace pattern. Record missing prerequisites instead of claiming integration success.
- [x] Build the existing Docker image, run the isolated local demo, and verify the four Swagger operations. Inspect final Git diff, nested proto status, and generated-file exclusions. No AWS deployment or push is part of this implementation. The user's implement-skill invocation authorizes local commits.

## Self-review

All spec sections map to the five flows above. The ten-node table preserves the original first six rewards and adds Gold, Player XP, Gems, and a distinct Legendary Hero. All mutation tasks use the same concrete DAO transaction and receipt contract; the separate state item avoids modifying every shop Player constructor. The five Review Focus cases have assigned tests. Implementation is proposed inline, one flow at a time, with review after each completed flow.

## Final verification

18 unit tests and 27 HTTP integration tests passed, including the original login/shop regressions. Client: 27 tests and production build passed. The built Docker app returned Swagger HTTP 200 and completed the ten-node demo. Standards/spec review identified a generated-ID length defect; a RED→GREEN test now enforces room for the season suffix. Handler naming follows the user's explicit `BattlePassHandler` request.
