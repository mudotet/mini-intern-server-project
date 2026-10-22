# Proto Studio Implementation Plan

> **For agentic workers:** Use the approved delegated implementation method and keep one writer per file.

**Goal:** Deliver and run the local schema form editor, diff, real validation, and ZIP export.

**Architecture:** Independent tool service in `tool/` and Java subproject in `server/`, preserving root commands and local game data.

**Tech Stack:** React, Vite, Tailwind CSS, Node HTTP, Protobuf descriptors, protoc, fflate, diff, Playwright.

**Spec:** `docs/superpowers/specs/2026-10-21-proto-studio-design.md`.

## Global Constraints

Product coding defaults to `gpt-6.1-sol` with `medium` reasoning, with explicit task-specific overrides only when warranted.
Preserve AGENTS.md, existing .codex settings, user changes, credentials, and Docker data.
No remote writes or automatic commits.
Use tests before nontrivial mutation/parser behavior and verify the working user flow.

## Review Focus

Preserve nested declarations, field options, comments, reserved ranges, and imports during source edits.
Refuse export for drafts that do not compile.
Treat the empty remote as an honest import error and keep the local demo usable.
Keep draft storage and changed-revision actions safe against accidental loss.
Keep the workspace usable at small laptop and mobile widths with accessible dialogs.

## Tasks

- [x] Backend worker owns `tool/package.json`, lockfile, `tool/server/**`, `tool/test/**`, `tool/.gitignore`, and installs the shared frontend dependencies declared in the spec.
- [x] Frontend worker owns `tool/src/**`, `tool/index.html`, and `tool/vite.config.js`, matching the shared API contract and design reference.
- [x] Migration worker owns Java relocation, root Gradle settings, existing scripts, Docker build paths, root ignore rules, README, and existing path references in docs except this spec/plan/ADR/CONTEXT.
- [x] Coordinator verifies API, browser flows and visuals, delegates necessary corrections to the owning worker, and updates the final verification notes.
- [x] Finish with the local tool server running and a concise endpoint and limitations report.
