# Proto Studio Local Demo

The user approved implementation without further design gates after the grill-with-docs session.
Build a local React and Tailwind workspace for nontechnical contributors to edit schema through forms, compare original and draft source, validate with real protoc, and download all draft proto files as a ZIP.
The canonical remote repository is `mudotet/proto_mini-intern-server-project`; remote loading requires a full commit SHA and never writes to GitHub.
The new remote is currently empty, so the app must also provide an explicitly labelled local demo using the game's existing schemas.
Do not fabricate a remote revision or push schemas without a separate request.

## Design

Use `design/proto-studio-reference.png` as the workspace visual reference.
Its useful structure is a compact global header, repository source strip, 250px searchable sidebar, spacious central editor, quiet contextual help column, and persistent bottom actions.
Use off-white and white surfaces, graphite text, a forest-green accent, warm orange message markers, subtle borders, 8px radii, and Vietnamese UI copy.
Use Be Vietnam Pro for legible Vietnamese text and a monospace family only for technical names and source.
Use an installed icon family rather than hand-drawn SVG.
The generated reference includes illustrative filenames and type labels; actual UI content must come from the current schemas.
Keep forms accessible with labels, keyboard focus, Escape dismissal, actionable errors, reduced-motion support, and responsive layouts.
Show real loading, validation, unsaved, empty-search, import-error, and export states.

## Repository Layout

Move the Java source tree and build definition into `server/`, retaining the root Gradle wrapper, root infrastructure, existing Docker data paths, and existing root startup commands.
Use a root Gradle multi-project configuration with `server` as the Java subproject and retain the distribution name `mini-game-server`.
Place the independent demo in `tool/` with its own npm package and a loopback-only Node HTTP service that also hosts Vite during development.
Do not read, move, print, or rewrite `.env`, credentials, Redis data, DynamoDB data, or AGENTS.md.
Submodule wiring is deferred until the remote proto repository contains a published schema commit.

## Tool API Contract

All JSON file sets are arrays of `{path, content}` objects, with paths relative to the schema root.
`GET /api/workspace` returns `{files, revision, analysis}` using the local source tree.
`revision` has `{source: 'local'|'github', repository: string, sha: string|null, label: string}`.
`POST /api/github` accepts `{repository: 'owner/repo', commit: '<full SHA>'}` and returns the same workspace shape after reading the entire `.proto` tree at that commit.
Remote loading rejects invalid commits, empty repositories, missing schema files, API errors, and truncated trees with a human-readable message.
`POST /api/analyze` accepts `{files}` and returns `analysis`.
`analysis` has `{valid: boolean, diagnostics: [{file, line, column, message}], files: [{path, package, messages, enums}]}`.
Messages are flattened including nested user-defined messages and excluding synthetic map entries.
Each message has `{name, fullName, fields}`.
Each field has `{name, number, type, rule: 'single'|'optional'|'repeated', keyType?, valueType?}`.
Scalar types use Protobuf names; user-defined types use full names without a leading dot; maps use `type: 'map'` with keyType and valueType.
Enums are flattened with `{name, fullName, values: [{name, number}]}`.
`POST /api/mutate` accepts `{files, path, operation}` and returns `{files, analysis}` on success.
Operations are `{kind: 'addField'|'updateField'|'deleteField', target: messageFullName, fieldNumber?: oldNumber, field?: fieldObject}`, `{kind: 'addMessage'|'addEnum', name, parent?: fullName}`, and `{kind: 'addEnumValue'|'updateEnumValue'|'deleteEnumValue', target: enumFullName, oldName?: string, value?: {name,number}}`.
For addField, omit number or send null to allocate the next available number, skipping existing/reserved/illegal ranges.
The backend validates changes and returns 422 with `{error, diagnostics}` without accepting an invalid mutation.
`POST /api/export` accepts `{files}`, validates them again, and returns `application/zip` preserving relative paths.
Every JSON error uses `{error: string, diagnostics?: []}`.

## Editing and Persistence

Load all declarations and types from schema descriptors produced by protoc with source information.
Apply surgical edits using source spans so unrelated comments, options, imports, and reserved declarations survive export.
Support adding messages, fields, enums and enum values, editing/deleting fields and enum values, and map/repeated types.
Existing complex constructs must remain intact even when not editable in the first form version.
No regular-expression-only whole-file parser or whole-file regeneration that drops declarations is acceptable.
Compile complete drafts with imports; defer comprehensive old/new compatibility checks.
Keep draft and base files separately, support restore, persist drafts in localStorage with guarded parsing, and invalidate validation state after edits.
Compare original and draft text in a readable side-by-side diff and offer a read-only source view.
Changing source/revision with unsaved edits must require a clear confirmation.

## Verification

Use meaningful Node tests for preservation, field/enum mutations, map fields, reserved-number allocation, rejected invalid schemas, safe paths, ZIP contents, and GitHub error handling.
Use browser tests for demo load, search, form edits, message/enum creation, diff, validation, ZIP download, reload recovery, failed remote import, and mobile layout.
Run the Java compile/unit suite after relocation and integration tests against existing local dependencies where available.
Keep generated artifacts and secrets out of Git and report exactly which checks ran.
