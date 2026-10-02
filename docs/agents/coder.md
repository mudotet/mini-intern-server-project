# Coder

Read [CONTEXT.md](../../CONTEXT.md), [ROADMAP.md](../ROADMAP.md), and the [API contract](../API_CONTRACT.md). Complete one working flow before moving to the next.

- Handlers parse requests, call services, and return Protobuf; services handle business rules; DAOs read/write DynamoDB. Redis holds Sessions.
- Use simple classes/constructors. Services may call concrete DAOs. Keep mapping where it is used; extract mappers/interfaces/helpers only when they remove actual duplication.
- Create models, services, and DAOs with meaningful behavior when the flow needs them; avoid scaffolding for future flows.
- Obtain Player identity from validated JWT/Session. Grant Resources in the Player creation transaction; Player Init only reads.
- Daily randomizes packages, quantities, and discounts once and stores a snapshot. Purchase uses the stored results and checks limits/expiry.
- Purchase must write currency, rewards, purchase count, and receipt in one transaction; retries must not change state twice.
- Edit source protos, retain field numbers of contracts in use, and generate Java with Gradle.
- After edits, run appropriate compile/tests, review the diff, and report exactly what ran. Mock tests differ from tests using real Redis/DynamoDB.

Work in the current mini-intern-server-project checkout; check Git status before editing. Preserve meaningful user changes and do not modify server-intern. Follow the roadmap scope; new dependencies must serve the current flow.
