# Mini game server and Proto Studio

This repository contains two projects:

| Directory | Project | Build and runtime |
| --- | --- | --- |
| [`tools/`](tools/README.md) | React Proto Studio for editing and exporting Protobuf schemas | Node.js and npm |
| [`server/`](server/README.md) | Java game API: Login, Player Init, Static/Daily Shop, Purchase, Battle Pass | Java 11, Gradle, Redis, DynamoDB |

The server pins the [canonical schema repository](https://github.com/mudotet/proto_mini-intern-server-project) as a Git submodule at `server/src/main/proto`. The client loads a public GitHub revision configured in Source settings. The two projects have separate builds and run independently.

Initialize schemas after cloning:

```bash
git submodule update --init --recursive
```

To run the React app, see [tools/README.md](tools/README.md). To build, test, or run the game API, see [server/README.md](server/README.md). Use [CONTEXT-MAP.md](CONTEXT-MAP.md) for project terminology and [AGENTS.md](AGENTS.md) for scope routing. Shared policies live in [`docs/`](docs/README.md); each project owns its documentation and agent team.
