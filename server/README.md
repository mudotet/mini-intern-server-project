# Mini game server

Java 11 backend using Akka HTTP, Protobuf, Redis, and DynamoDB. It implements Login, Player Init, Static/Daily Shop, Purchase, and a ten-node Battle Pass.

Initialize the schema submodule once from the repository root:

```bash
git submodule update --init --recursive
```

All commands below run from this `server/` directory. The Gradle wrapper, Java sources, Docker configuration, deployment bundle, and server scripts are here.

```bash
./gradlew clean compileJava test installDist
bash scripts/run-local.sh
docker compose -f docker/docker-compose.yaml logs app
bash scripts/demo.sh
```

The startup script requires Docker Compose and Python 3, creates a random JWT secret in `.env` if missing, and starts the API, Redis, and DynamoDB Local. Open http://localhost:8080/swagger/ after the `Mini game API ready` log.

To run the HTTP integration tests with local dependencies:

```bash
docker compose -f docker/docker-compose.yaml up -d
./gradlew integrationTest
```

Stop the local services without deleting data:

```bash
docker compose --env-file .env -f docker/docker-compose.yaml --profile app stop
```

The source schemas live in `src/main/proto`, pinned to the [canonical repository](https://github.com/mudotet/proto_mini-intern-server-project). To adopt a reviewed commit, check out its full SHA in the submodule, run the build, and record the new submodule pointer from the repository root:

```bash
git -C src/main/proto fetch origin
git -C src/main/proto checkout REVIEWED_FULL_COMMIT_SHA
./gradlew compileJava test
git -C .. add server/src/main/proto
```

Generated Protobuf Java code belongs in `build/`, not in Git. See [`docs/`](docs/) for API, architecture, testing, and deployment instructions. Live AWS EC2 deployment remains unverified.

## Battle Pass demo

The shared pass has ten mixed Free/Premium nodes with one reward each. Every node requires another full 500 expBP; 510 EXP means one reached node and 10/500 toward the next. Premium costs 100 Gems. Heroes remain owned across passes; duplicates grant Gems by rarity.

After starting the local app, use **06 · Battle Pass** in Swagger or run:

```bash
bash scripts/battle-pass-demo.sh
```

The local Compose app explicitly enables `DEMO_QUEST_GRANTS_ENABLED=true`. Direct Java startup defaults to false, and the EC2 template does not enable this simulated Quest reward API. It accepts trusted-system-style EXP input solely for the demo; real mission verification is outside scope.

See [Battle Pass rules](docs/BATTLE_PASS.md) and [API contract](docs/API_CONTRACT.md). The shared template's stable origin is 2026-10-01 00:00 at UTC+07:00; consecutive passes last 30 days. Changing a published template's rewards or schedule requires a new template ID and deliberate rollout. Do not change definitions under the same ID during a running pass.

The Battle Pass request/response source files are indexed in [src/main/proto/game/app/battle_pass/README.md](src/main/proto/game/app/battle_pass/README.md). They live inside the schema Git submodule, so inspect its own history to see individual `.proto` changes. Application Java types use regular classes; DynamoDB "items" or "records" in the documentation describe stored data, not Java `record` declarations.
