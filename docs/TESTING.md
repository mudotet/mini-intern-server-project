# Demo testing

Use Java 11:

```bash
./gradlew test
docker compose -f docker/docker-compose.yaml up -d
./gradlew integrationTest
```

test passed 9 unit/route tests in local verification on 2026-10-02: 6 BaseApiHandler tests, 2 request-body failure tests (oversized stream and timeout), and 1 JWT test. Swagger, catalog, and purchase behavior are covered by integration tests, not these unit tests.

integrationTest passed 14 tests through real HTTP sockets with Redis + DynamoDB Local on 2026-10-02:

- Login retry/recovery, Account ownership, forged/expired JWTs, replaced/lost Sessions.
- Concurrent logins create only one identity and grant Resources once.
- Concurrent Daily reads retain the same snapshot; static remains stable.
- Daily permits 3 purchases and rejects the fourth; price/reward/discount remain unchanged.
- Receipt replay does not write state twice; different RequestId input is rejected.
- Competing requests for the final purchase allowance or final gold balance allow only one success.
- Four identical purchase requests write only once.
- Static permits more than 3 purchases; insufficient funds and invalid amount are rejected; Init does not refill.
- Advancing Clock to midnight creates a new cycle; the old offer cannot be purchased.
- Restarting HTTP server/handler/DAO preserves identity, Resources, snapshot, and receipt.
- JSON Login → Init → Shop → Purchase, JSON/auth/conflict errors, and replay of the same receipt through JSON/Protobuf.
- Information GET reads the correct current Player, post-purchase Resources, and Packages; it does not write version/snapshot or select another Player through a query.
- GET requires valid JWT/Session, accepts neither a body nor POST; GET cannot purchase.
- Session info counts time using Clock; old/lost Sessions or expired JWTs return 401, without returning credentials.

Each integration test uses a fresh test instance with a mini_test_<UUID> table name. @BeforeEach creates that table, its Redis namespace, and an HTTP server on an ephemeral port; @AfterEach deletes that table and only Redis keys matching its table prefix. There is no shared @BeforeAll table. Tests do not use the app's GAME_TABLE. IT_DYNAMODB_ENDPOINT and IT_REDIS_HOST/PORT can be changed for the local test environment.

The previous documentation dated 2026-09-30 is superseded by actual local verification on 2026-10-02: ./gradlew test integrationTest passed 9 unit + 14 integration tests. ./gradlew installDist and the repair-validation Docker build passed; the image ran as nonroot UID 10001. The rebuilt isolated app on port 18080 returned Swagger HTTP 200 and passed scripts/demo.sh for JSON Login/Init/Static/Daily/Purchase, Protobuf purchase retry, and all four information APIs. Dedicated validation table/keys were cleaned; existing user containers were untouched. The final ./gradlew clean compileJava test integrationTest build passed in 24 seconds. **Live AWS EC2 has not been verified.** DynamoDB Local coverage does not establish IAM/throttling/EC2/AWS DynamoDB behavior.

[demo.sh](../scripts/demo.sh) calls a running app and prints prices/rewards/Resources, not JWTs. Use DEMO_RESTART_LOCAL=1 bash scripts/demo.sh to restart the local Docker app and check receipt equality and Session validity afterward; this script does not compare the full Player or daily snapshot. Day changes are tested with Clock; there is no cheat endpoint.

## Proto Studio Local Demo

Run `npm run dev` from `tool/` and leave it running at http://127.0.0.1:5173.
In another terminal, run these checks from `tool/`:

```bash
npm test
npm run test:e2e
npm run build
```

Local verification on 2026-10-21 passed 27 compiler/API/diff/file-tree tests, ten browser acceptance tests, and the production frontend build.
The browser tests exercise nested folder navigation, search, declaration outlines, actual form mutations, source preservation, aligned comparison, draft recovery, failed import retention, validation, export confirmation and cancellation, downloaded ZIP contents, mobile/laptop layout, and keyboard focus.
Desktop, laptop, mobile, form, and export-dialog screenshots were visually inspected after the redesign.
Responsive browser coverage includes 320×568, 390×844, 768×1024, 1024×600, 1440×960, and landscape 844×390.
It checks field actions without horizontal table scrolling, 44px edit targets, file drawers, source/diff layouts, and scrollable form/export dialogs.
Responsive screenshots are saved in `tool/artifacts/responsive/`.
Compiler tests use real protoc; GitHub success/error tests use controlled HTTP responses rather than a live published schema commit.
Browser screenshots are written to `tool/artifacts/` and ignored by Git.
Set `CHROME_EXECUTABLE` to an installed Chrome executable or install the Playwright Chromium browser before running browser tests.

After moving the Java project into `server/`, `./gradlew compileJava test integrationTest installDist` passed 9 unit and 14 real Redis/DynamoDB Local integration tests on 2026-10-21.
All 51 moved Java/proto/resource/test files were verified byte-identical to their original Git content.
The updated Docker build and root `scripts/run-local.sh` started the game server successfully, with Swagger returning HTTP 200.
After publishing the canonical schema repository, the actual GitHub loader fetched commit `06a40d0da3a6c14d79acaf0fc40be086a0ad93db` and verified all 14 files byte-identical to the local schema set and valid under protoc.
No live AWS deployment was verified in this run.

## Submodule and publication verification

On 2026-10-22, `server/src/main/proto` was linked to the canonical schema repository at `06a40d0da3a6c14d79acaf0fc40be086a0ad93db`.
All 14 schema files and all 51 relocated Java/proto/resource/test files were verified byte-identical to their original contents.
`./gradlew clean compileJava test installDist` passed with 9 unit tests after linking the submodule, and `npm test` passed all 27 tool tests.
The 10 browser acceptance tests and production frontend build passed immediately before the shutdown request on 2026-10-21.
Integration tests were not rerun during shutdown because Redis and DynamoDB Local were stopped at the user's request.
