# Share proto schemas through a separate repository and commit revisions

The proto tool edits schema definitions only, not game configuration data.
The canonical schemas live in https://github.com/mudotet/proto_mini-intern-server-project, and the game server consumes that repository as a Git submodule.
The tool will accept a full commit SHA to select a ProtoRevision and load all schema files from that same commit through the GitHub API.
This replaces the earlier proposal to identify the selected version by branch name, because a branch can advance while a commit identifies a fixed snapshot.
Edits produce a ProtoDraft while the selected commit remains unchanged.

## Initial Delivery Flow

The initial tool loads schemas by commit SHA, supports editing and validation, and exports the resulting schema files as a ZIP for manual use.
The export includes the selected schema tree with draft changes applied and preserves relative paths so imports between repository files continue to resolve.
GitHub access in this flow is read-only; exporting does not create commits, push files, publish to Redis, or update the server's submodule.
Generated client/server code and game configuration data are outside this export.
Users manually commit the exported schemas and update consumers when they choose to adopt the changes.

## Editing Interface

The primary editing interface uses structured forms for messages, fields, and enums so nontechnical contributors do not need to write Protobuf source code.
The tool derives the forms from the selected schemas rather than hardcoding the game's message definitions.
Before export, users can compare the original revision and the draft in a side-by-side text diff showing added, changed, and removed schema content.
The text comparison complements the form-based editing workflow.
Exports must preserve existing imports, options, reserved declarations, and definitions outside the edited elements.

## Initial Validation Scope

The initial validation checks that the complete draft schema set compiles with its imports resolved and blocks export when this check fails.
The initial workflow assumes client and server deliberately adopt the same exported schema version.
Comprehensive compatibility analysis across old and new consumers is deferred so the first version stays focused on form editing, text comparison, and manual export.

## Local Demo Implementation

The local demo uses `tool/` for the independent workspace and `server/` for the Java game subproject, with Gradle commands and Docker infrastructure retained at the repository root.
Drafts and their original schema snapshot are stored in browser local storage.
The forms support adding messages and enums, adding/editing/removing fields, and adding/editing/removing enum values, including repeated and map field types.
The canonical repository's initial 14-schema commit is `06a40d0da3a6c14d79acaf0fc40be086a0ad93db`, published on 2026-10-21.
The tool's GitHub loader successfully fetched that revision, verified byte-identical schema contents, and compiled the complete set.
Actual local schemas remain available as a labelled demo.
The server pins this commit through the `server/src/main/proto` Git submodule.
Initialize existing clones with `git submodule update --init --recursive`; use `git clone --recurse-submodules` for new clones.
