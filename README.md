# Mini game server

Backend demo using Java 11, Akka HTTP, Protobuf, Redis, and DynamoDB. Implemented flows: Login → Player Init → Static/Daily Shop → Purchase.

## Run locally

Requires Java 11, Docker Compose, Python 3, curl, and protoc. From the repository directory:

```bash
bash scripts/run-local.sh
docker compose -f docker/docker-compose.yaml logs app
bash scripts/demo.sh
```

The script generates a random JWT secret in .env if no valid secret exists, builds the Java distribution, and starts the app + Redis + DynamoDB Local. Wait for the “Mini game API ready” log before running the demo. The app is at http://localhost:8080; local data is stored in docker/data and ignored by Git.

## Swagger UI

Open [http://localhost:8080/swagger/](http://localhost:8080/swagger/) to view the 10 endpoints and try them using JSON:

1. Create/copy a RequestId at the top of the page and paste it into Login's X-Request-Id; execute with body {}.
2. Copy access_token from the response → Authorize, and paste the token.
3. Call Player Init, Static, and Daily; call Purchase with a returned offer ID and amount=1.
4. Use a new RequestId for a new purchase; keep the same ID/input for retries.

Swagger assets are bundled with the app, so the page needs no CDN. JSON uses the same schema/handler/service as Protobuf; int64 values (quantity/time) are strings. See the [API contract](docs/API_CONTRACT.md).

### 05 · Information

After Login → Authorize, use the following GET APIs to retrieve display data. No body or RequestId is required; responses are always JSON.

| API | Data |
| --- | --- |
| GET /api/info/player | account_id and player_id of the current Player |
| GET /api/info/resources | Latest gold/gem/XP Resources; call again after Purchase |
| GET /api/info/packages | Package definitions: base price, default rewards, daily quantity ranges |
| GET /api/info/session | Session Account/Player, expires_at, server_time, remaining_seconds |

These read APIs do not grant Resources again or create DailyOffers. The session API does not return credentials; remaining time refers to JWT expiry, and the Redis Session may be lost or replaced earlier.

## Read the code

Request → handler → service → GameDao → DynamoDB/Redis → response.

- Login creates Account/Device/Player and grants 1,000 gold, 100 gem, and 0 XP once.
- Session JWTs expire after 12 hours; authentication checks signature/expiry and the Redis Session.
- Static has 6 packages with fixed prices/rewards.
- Daily has 3 distinct packages/player/day; quantity and a 10/20/30% discount are randomized and retained for the day; 3 purchases/offer.
- Purchase updates Player and receipt in the same DynamoDB transaction; retries return the original receipt.

## Test and deploy

```bash
./gradlew test
./gradlew integrationTest
bash scripts/package-ec2.sh
```

integrationTest requires running Redis/DynamoDB Local; it uses a separate namespace and cleans up test data. The EC2 deployment bundle is in deploy/. The AWS runtime uses an IAM role, not local credentials. **Local verification passed 9 unit + 14 integration tests, installDist, the repair-validation Docker build (nonroot UID 10001), and the isolated app demo on port 18080 on 2026-10-02. Swagger returned HTTP 200; JSON game flows, Protobuf purchase retry, and all four information APIs passed. Dedicated validation data was cleaned; existing user containers were untouched. Live AWS EC2 remains unverified; the final ./gradlew clean compileJava test integrationTest build passed in 24 seconds.**

Read [running/deployment](docs/DEPLOYMENT.md), [API](docs/API_CONTRACT.md), [architecture](docs/ARCHITECTURE.md), [testing](docs/TESTING.md), [roadmap](docs/ROADMAP.md), and the [glossary](CONTEXT.md).
