# Proto Studio tools

React app for browsing, editing, validating, comparing, and exporting Protobuf schemas. Its local Node API serves the app and performs schema operations. The game server, Redis, and DynamoDB are not needed to run it.

Requires Node.js 22.12 or newer and `protoc`. The tool finds `protoc` on PATH, through `PROTOC`, or in the Gradle cache after building the Java server.

From this `tools/` directory:

```bash
npm ci
npm run dev
```

Open http://127.0.0.1:5173. The main dashboard starts empty. Open Source settings and enter a public repository plus a full commit SHA to load schemas. GitHub loading is read-only and requires a public repository plus a full 40-character commit SHA. Drafts are stored in browser local storage; export a ZIP for a portable copy.

```bash
npm test
npm run build
npm run test:e2e
```

Browser tests require Chrome or Playwright Chromium. Set `CHROME_EXECUTABLE` if needed. Product documentation and historical plans are indexed in [`docs/README.md`](docs/README.md). Agent instructions and the tools team are in [AGENTS.md](AGENTS.md) and [.codex/TEAM.md](.codex/TEAM.md).

The English admin dashboard shows schema totals, an interactive field-count chart, field types, and import relationships. Light and dark themes follow the system initially and remember your choice. The schema editor provides file navigation and a declaration outline. Compare opens a viewport-sized overlay with split/unified views, inline highlights, and a changes-only filter; Escape returns to the editor. Edits remain local until ZIP export and are never published to GitHub automatically.

The UI/UX Pro Max Codex skill is installed under `tools/.agents/skills/ui-ux-pro-max` (relative to the repository root) using `uipro init --ai codex`.
