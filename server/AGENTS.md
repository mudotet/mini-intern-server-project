# Java game server

Read [CONTEXT.md](CONTEXT.md), [docs/ROADMAP.md](docs/ROADMAP.md), and the flow's source before editing. Start at [docs/README.md](docs/README.md) for API, architecture, tests, and deployment.

## Implementation

- Use Java 11, Akka HTTP, Protobuf, Redis, and DynamoDB from this build.
- Complete one flow at a time: handler → service → DAO → data → response.
- Use regular classes and simple constructors. Services may call concrete DAOs; add interfaces, base classes, and helpers only for an actual need.
- Create models, services, and DAOs only when a flow needs meaningful behavior. Preserve authorization, transactions, and retry safety.
- Demo flows include Login, Player Init, static/daily shop, Purchase, and Battle Pass. EC2 deployment is documented; live AWS remains unverified. See the roadmap for dated local evidence.

## Contracts and checks

- `src/main/proto` is a Git submodule of `https://github.com/mudotet/proto_mini-intern-server-project`. Edit source schemas, preserve existing field numbers, and generate Java with Gradle; do not edit generated Java.
- When publishing schema changes, commit and push the proto repository first, then update the parent repository's submodule pointer. A parent commit alone does not publish schemas.
- Run commands from `server/`: `./gradlew compileJava test`; run `./gradlew integrationTest` when Redis and DynamoDB Local are available. Report local and live AWS evidence separately.
- For delegated work, read [.codex/TEAM.md](.codex/TEAM.md). Role guides are [coder](docs/agents/coder.md), [tester](docs/agents/tester.md), [planning](docs/agents/pm.md), and [deployer](docs/agents/deployer.md).
