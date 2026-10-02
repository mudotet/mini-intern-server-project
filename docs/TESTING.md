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
