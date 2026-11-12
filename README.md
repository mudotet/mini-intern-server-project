# Mini game server

Backend demo using Java 11, Akka HTTP, Protobuf, Redis, and DynamoDB.
Implemented flows include Login, Player Init, Static/Daily Shop, Purchase, and the weekly Battle Pass.

## Repository layout

- `server/`: Java game source, schemas, resources, tests, and Gradle build.
- `tool/`: independent local Proto Studio demo.
- `mobile/`: Expo mobile demo, pinned as a Git submodule from [mobile-demo-for-mini-intern-project](https://github.com/mudotet/mobile-demo-for-mini-intern-project).
- Root Gradle wrapper, scripts, Docker configuration, and deployment bundle retain their existing locations.

The canonical schema repository is [mudotet/proto_mini-intern-server-project](https://github.com/mudotet/proto_mini-intern-server-project).
This release pins the 15-schema revision `90b06fc283a1a6c3b47b41f4e48cb0c53e3e78f2`, including the mobile Battle Pass contract.
Enter the repository name and this full SHA in Proto Studio's `Mở GitHub` dialog.
The server pins that repository as a Git submodule at `server/src/main/proto`.
Proto Studio's local demo reads the same initialized schema directory.

## Clone with submodules

```bash
git clone --branch release/mobile-demo-2026-11-12 --recurse-submodules https://github.com/mudotet/mini-intern-server-project.git
cd mini-intern-server-project
```

For an existing clone, switch to `release/mobile-demo-2026-11-12`, then run `git submodule update --init --recursive` from the repository root before building the server or opening the local schema demo.
Run `git submodule status` to check the pinned revision.
See the [server README](server/README.md) for schema update and backend setup instructions.

This release preserves the mobile demo's weekly six-milestone Battle Pass contract.
The main branch contains a separate server/tools reorganization and Battle Pass implementation.

## Run the mobile demo

Initialize `mobile/` with `git submodule update --init mobile` from this repository root.
Use Node.js 24 LTS, Java 11, and Docker for the local demo.

```bash
cd mobile
npm ci
npm run backend
```

In a second terminal, run `cd mobile` from the repository root and `npm start -- --lan`.
Open the project in Expo Go SDK 58 on an iPhone on the same Wi-Fi.
See [the mobile README](mobile/README.md) for setup, the 3D dealer, Shop, Battle Pass, and verification.
Changes to the client are committed and pushed in `mobile/`, then the parent repository records the new mobile commit.

## Docker release 1.1.0

Build the backend distribution with Java 11, then create the versioned runtime image.
The iPhone client runs through Expo Go and connects to this container over Wi-Fi.

```bash
docker compose --env-file /dev/null -f docker/docker-compose.yaml up -d --wait redis dynamodb
./gradlew clean compileJava test integrationTest installDist
docker build -f docker/application/Dockerfile -t mini-game-server:1.1.0 -t mini-game-server:demo .
export JWT_SECRET="$(python3 -c 'import secrets; print(secrets.token_hex(32))')"
GAME_IMAGE=mini-game-server:1.1.0 GAME_TABLE=mini_mobile_demo GAME_BIND_ADDRESS=0.0.0.0 \
  docker compose --env-file /dev/null -f docker/docker-compose.yaml --profile app up -d
```

Port 8080 must be available before starting the app container.
Open [Swagger](http://localhost:8080/swagger/) and configure the iPhone Connection with the Mac's Wi-Fi address on port 8080.
The demo table and Redis/DynamoDB data volumes survive container restarts.
Keep the same JWT secret when recreating the app container to retain existing sessions, or let the client sign in again.
Omit `GAME_BIND_ADDRESS=0.0.0.0` to use the default loopback-only binding.

Export the backend image for another ARM64 Docker machine.

```bash
mkdir -p build
docker save mini-game-server:1.1.0 | gzip > build/mini-game-server-1.1.0-arm64.tar.gz
docker load -i build/mini-game-server-1.1.0-arm64.tar.gz
```

The archive contains the backend runtime image.
The Compose file supplies Redis, DynamoDB Local, and their administration interfaces.

## Run Proto Studio

Requires Node.js 22.12 or newer.
The tool finds `protoc` on PATH, through `PROTOC`, or in the project's existing Gradle cache.
If no compiler is available, run `./gradlew :server:generateProto` from the repository root first.

```bash
cd tool
npm install
npm run dev
```

Open [http://127.0.0.1:5173](http://127.0.0.1:5173).
The tool runs independently of the game server, Redis, and DynamoDB.
Use forms to edit messages, fields, and enums, review the old/new text diff, validate, and download a ZIP that preserves schema paths.
Browse nested folders in the file sidebar and select messages or enums from the file outline.
On phones and tablets, file navigation uses a drawer and field editing uses labelled cards with touch-friendly actions.
`Xuất ZIP` opens a review dialog; `Tải ZIP` downloads every current workspace schema, including unchanged files, with relative folders preserved.
Drafts are saved in this browser's local storage; downloading a ZIP provides a portable copy.
GitHub loading is read-only and requires a public repository with schema files and a full commit SHA.
The tool does not publish schemas to GitHub or update consumers automatically.

```bash
npm test
npm run test:e2e
npm run build
```

Browser tests use an installed Chrome when available; set `CHROME_EXECUTABLE` to its path, or run `npx playwright install chromium`.
Keep the local tool running while executing browser tests.

## Run locally

Requires Java 11, Docker Compose, Python 3, curl, and protoc.
From the repository directory:

```bash
bash scripts/run-local.sh
docker compose -f docker/docker-compose.yaml logs app
bash scripts/demo.sh
```

The script generates a random JWT secret in .env if no valid secret exists, builds the Java distribution, and starts the app + Redis + DynamoDB Local.
Wait for the “Mini game API ready” log before running the demo.
The app is at http://localhost:8080; local data is stored in docker/data and ignored by Git.

Stop the local backend and its dependencies without removing data:

```bash
docker compose --env-file .env -f docker/docker-compose.yaml --profile app stop
```

Stop Proto Studio with Ctrl+C in its terminal.

## Swagger UI

Open [http://localhost:8080/swagger/](http://localhost:8080/swagger/) to view the 10 endpoints and try them using JSON:

1. Create/copy a RequestId at the top of the page and paste it into Login's X-Request-Id; execute with body {}.
2. Copy access_token from the response → Authorize, and paste the token.
3. Call Player Init, Static, and Daily; call Purchase with a returned offer ID and amount=1.
4. Use a new RequestId for a new purchase; keep the same ID/input for retries.

Swagger assets are bundled with the app, so the page needs no CDN.
JSON uses the same schema/handler/service as Protobuf; int64 values (quantity/time) are strings.
See the [API contract](docs/API_CONTRACT.md).

### 05 · Information

After Login → Authorize, use the following GET APIs to retrieve display data.
No body or RequestId is required; responses are always JSON.

| API | Data |
| --- | --- |
| GET /api/info/player | account_id and player_id of the current Player |
| GET /api/info/resources | Latest gold/gem/XP Resources; call again after Purchase |
| GET /api/info/packages | Package definitions: base price, default rewards, daily quantity ranges |
| GET /api/info/session | Session Account/Player, expires_at, server_time, remaining_seconds |

These read APIs do not grant Resources again or create DailyOffers.
The session API does not return credentials; remaining time refers to JWT expiry, and the Redis Session may be lost or replaced earlier.

## Read the code

Request → handler → service → GameDao → DynamoDB/Redis → response.

- Login creates Account/Device/Player and grants 1,000 gold, 100 gem, and 0 XP once.
- Session JWTs expire after 12 hours; authentication checks signature/expiry and the Redis Session.
- Static has 6 packages with fixed prices/rewards.
- Daily has 3 distinct packages/player/day; quantity and a 10/20/30% discount are randomized and retained for the day; 3 purchases/offer.
- Purchase updates Player and receipt in the same DynamoDB transaction; retries return the original receipt.

## Test and deploy

```bash
./gradlew compileJava test installDist
./gradlew integrationTest
bash scripts/package-ec2.sh
```

integrationTest requires running Redis/DynamoDB Local; it uses a separate namespace and cleans up test data.
The EC2 deployment bundle is in deploy/.
The AWS runtime uses an IAM role, not local credentials.
**Local verification passed 9 unit + 14 integration tests, installDist, the repair-validation Docker build (nonroot UID 10001), and the isolated app demo on port 18080 on 2026-10-02.
Swagger returned HTTP 200; JSON game flows, Protobuf purchase retry, and all four information APIs passed.
Dedicated validation data was cleaned; existing user containers were untouched.
Live AWS EC2 remains unverified; the final ./gradlew clean compileJava test integrationTest build passed in 24 seconds.**

Read [running/deployment](docs/DEPLOYMENT.md), [API](docs/API_CONTRACT.md), [architecture](docs/ARCHITECTURE.md), [testing](docs/TESTING.md), [roadmap](docs/ROADMAP.md), and the [glossary](CONTEXT.md).
