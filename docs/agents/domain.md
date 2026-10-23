# Domain documentation

Read [CONTEXT-MAP.md](../../CONTEXT-MAP.md) to select a project glossary before exploring or naming domain concepts:

- Server terms: [server/CONTEXT.md](../../server/CONTEXT.md).
- Proto Studio terms: [tools/CONTEXT.md](../../tools/CONTEXT.md).
- Cross-project decisions: [docs/adr/](../adr/).

Use the selected glossary's terms in issues, designs, code, and tests. Preserve the distinction between game state and schema editing. Flag a contradiction with an existing ADR explicitly before changing the decision.

Keep project-specific domain explanations and decisions inside that project's documentation; shared decisions belong in root `docs/adr/`. Update the context map when adding a project context. Historical plans do not override current contracts or source.
