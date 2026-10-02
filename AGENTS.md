# Mini game server demo

Read CONTEXT.md for terminology and docs/ROADMAP.md for the current flow. Check the source and Git status in the current checkout before editing.

## Working approach

- Implement one flow at a time: handler → service → DAO → data → response.
- Use Java 11, Akka HTTP, Protobuf, Redis, and DynamoDB already included in the build.
- Services may call concrete DAOs; create interfaces/base classes/helpers when a real need arises.
- Create models/services/DAOs when the implemented flow needs them; each file must have meaningful content and a clear role.
- Keep changes within mini-intern-server-project. Preserve meaningful user changes; cleanup of empty scaffolding was requested in this session.
- Demo scope: login, Player Init, static/daily shop, purchase, and EC2 + DynamoDB deployment.
- Login/Player Init/shop/purchase flows are implemented; distinguish local tests from live AWS verification. Local verification passed 9 unit + 14 integration tests and the isolated Docker demo on 2026-10-02; live AWS EC2 remains unverified. The final ./gradlew clean compileJava test integrationTest build passed in 24 seconds.
- For role-specific work, read the matching guide in docs/agents/: coder.md, tester.md, pm.md, or deployer.md.

## Agent skills

### Issue tracker

Issues are tracked in this repository's GitHub Issues. See docs/agents/issue-tracker.md.

### Triage labels

Triage uses the five canonical labels without aliases. See docs/agents/triage-labels.md.

### Domain docs

Domain documentation uses a single-context layout. See docs/agents/domain.md.
