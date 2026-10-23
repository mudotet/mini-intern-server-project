# Proto Studio client

React app for browsing, editing, validating, comparing, and exporting Protobuf schemas. Its local Node API serves the app and performs schema operations. The game server, Redis, and DynamoDB are not needed to run it.

Requires Node.js 22.12 or newer and `protoc`. The tool finds `protoc` on PATH, through `PROTOC`, or in the Gradle cache after building the Java server.

From this `client/` directory:

```bash
npm ci
npm run dev
```

Open http://127.0.0.1:5173. For the local schema demo, initialize the repository submodule with `git submodule update --init --recursive` from the repository root. GitHub loading is read-only and requires a public repository plus a full 40-character commit SHA. Drafts are stored in browser local storage; export a ZIP for a portable copy.

```bash
npm test
npm run build
npm run test:e2e
```

Browser tests require Chrome or Playwright Chromium. Set `CHROME_EXECUTABLE` if needed. Product design notes and historical plans are in [`docs/`](docs/).
