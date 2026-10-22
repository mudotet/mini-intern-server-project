# Mini game server

Java 11 backend using Akka HTTP, Protobuf, Redis, and DynamoDB.
Implemented flows cover login, Player Init, static/daily shop, and purchase.

## Clone and initialize schemas

Run these commands from the directory where you keep projects:

```bash
git clone --recurse-submodules https://github.com/mudotet/mini-intern-server-project.git
cd mini-intern-server-project
git submodule status
```

For an existing clone, run from the repository root:

```bash
git submodule update --init --recursive
git submodule status
```

The schema submodule is at `server/src/main/proto` and points to the separate [canonical schema repository](https://github.com/mudotet/proto_mini-intern-server-project).
The initial pinned revision is `06a40d0da3a6c14d79acaf0fc40be086a0ad93db`.
Initialize the submodule before building; updating it without `--remote` checks out the revision recorded by this repository.

## Build and run

Use Java 11 and the Gradle wrapper in the repository root.
All commands below run from that root; if your terminal is inside `server/`, run `cd ..` first.

```bash
./gradlew compileJava test installDist
bash scripts/run-local.sh
```

The local startup script requires Docker Compose and Python 3.
It builds the distribution and starts the app, Redis, and DynamoDB Local.
Wait for `Mini game API ready` in the app logs, then open [Swagger UI](http://localhost:8080/swagger/).
Use the root `.env.example` as a configuration reference; keep `.env` and credentials local and never commit secrets.
The startup script creates a JWT secret when one is missing.

To stop local services while retaining their data, run from the repository root:

```bash
docker compose --env-file .env -f docker/docker-compose.yaml --profile app stop
```

## Update the pinned schema version

Schema changes belong in the canonical schema repository and must be reviewed there first.
To adopt a reviewed commit, run from this repository's root and replace `REVIEWED_FULL_COMMIT_SHA` with its full SHA:

```bash
git -C server/src/main/proto fetch origin
git -C server/src/main/proto checkout REVIEWED_FULL_COMMIT_SHA
./gradlew compileJava test
git add server/src/main/proto
git commit -m "Update schema revision"
```

The root commit records the selected schema revision; it does not copy the schema files into this repository.
Gradle generates Protobuf Java code during the build; do not edit or commit generated code under `server/build/`.
See the [root README](../README.md), [API contract](../docs/API_CONTRACT.md), and [deployment guide](../docs/DEPLOYMENT.md) for the complete workflow.
