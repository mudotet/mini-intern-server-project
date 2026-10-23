# Mini game server and Proto Studio

This repository contains two projects:

| Directory | Project | Build and runtime |
| --- | --- | --- |
| [`client/`](client/README.md) | React Proto Studio for editing and exporting Protobuf schemas | Node.js and npm |
| [`server/`](server/README.md) | Java game API: Login, Player Init, Static/Daily Shop, Purchase, Battle Pass | Java 11, Gradle, Redis, DynamoDB |

The server pins the [canonical schema repository](https://github.com/mudotet/proto_mini-intern-server-project) as a Git submodule at `server/src/main/proto`. The client can read that local schema set or load a public GitHub revision. The two projects have separate builds and run independently.

Initialize schemas after cloning:

```bash
git submodule update --init --recursive
```

To run the React app, see [client/README.md](client/README.md). To build, test, or run the game API, see [server/README.md](server/README.md). Shared terminology and project guidance remain in [CONTEXT.md](CONTEXT.md), [AGENTS.md](AGENTS.md), and [`docs/`](docs/README.md).
