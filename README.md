# Mini game server

Backend demo using Java 11, Akka HTTP, Protobuf, Redis, and DynamoDB.
Implemented flows: Login → Player Init → Static/Daily Shop → Purchase.

## Repository layout

- `server/`: Java game source, schemas, resources, tests, and Gradle build.
- `tool/`: independent local Proto Studio demo.
- Root Gradle wrapper, scripts, Docker configuration, and deployment bundle retain their existing locations.

The canonical schema repository is [mudotet/proto_mini-intern-server-project](https://github.com/mudotet/proto_mini-intern-server-project).
Its initial 14-schema revision is `06a40d0da3a6c14d79acaf0fc40be086a0ad93db`; enter the repository name and this full SHA in Proto Studio's `Source settings` dialog.
The server pins that repository as a Git submodule at `server/src/main/proto`.
Proto Studio opens the main dashboard with no schemas loaded. Configure a public GitHub repository and full commit SHA in Source settings to load schemas; no local demo is loaded.

## Clone with the schema submodule

```bash
git clone --recurse-submodules https://github.com/mudotet/mini-intern-server-project.git
cd mini-intern-server-project
```

For an existing clone, run `git submodule update --init --recursive` from the repository root before building the server or opening the local schema demo.
Run `git submodule status` to check the pinned revision.
See the [server README](server/README.md) for schema update and backend setup instructions.

## Run Proto Studio

After importing a repository and commit, Proto Studio opens an admin overview with live file/message/field totals, an interactive field-count chart, a field-type breakdown, and a compiled import explorer. Switch between light and dark themes in the header; the initial theme follows your system and an explicit choice is saved in the browser. The interface is in English. Open Schema editor for file navigation and declaration editing. Compare opens a large overlay above the editor (Escape or Back to editor closes it) and shows the original and draft in split or unified views, with red removals, green additions, inline word highlights, and a changes-only filter. Open the changed-file menu to jump to a draft diff; changes remain local until you export them. The UI/UX Pro Max Codex skill is installed under `.agents/skills/ui-ux-pro-max` using `uipro init --ai codex`.

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
