# Project contexts

| Project | Terminology | Entry point |
| --- | --- | --- |
| Java game server | [Server glossary](server/CONTEXT.md) | [Server documentation](server/docs/README.md) |
| React / Node Proto Studio | [Tools glossary](tools/CONTEXT.md) | [Tools documentation](tools/docs/README.md) |

Proto Studio edits schema drafts; the game server consumes committed schemas from the separate proto repository. Exporting a draft does not update the server. The shared contract is documented in [the schema revision decision](docs/adr/0001-proto-schema-versions-by-commit.md).
