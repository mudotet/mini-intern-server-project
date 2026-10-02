# Demo workflow

1. Read [CONTEXT.md](../CONTEXT.md), [ROADMAP.md](ROADMAP.md), Git status, and the flow's source.
2. Choose one small end-to-end behavior: request → handler → service → DAO → data → response.
3. Update the contract if needed, then implement meaningful classes for that flow.
4. Check behavior, compile/test, and review the diff.
5. Record actual results, what was not run, and the next step.

Use handlers → services → concrete DAOs. Domain models hold state/rules; map Protobuf where it is used. Add interfaces, abstract bases, separate mappers, or store adapters only when there is a real need.

Redis holds Sessions; DynamoDB stores Account/Device/Player, daily snapshots, and purchase receipts. Initial package configuration is a file bundled with the app. The demo target is one EC2 instance running app + Redis, with AWS DynamoDB.

The existence of classes/protos does not prove endpoints work. The main flows have HTTP tests with Redis/DynamoDB Local; AWS requires separate verification. Local verification passed 9 unit + 14 integration tests and the isolated Docker demo on 2026-10-02; live AWS EC2 remains unverified. The final ./gradlew clean compileJava test integrationTest build passed in 24 seconds.

Preserve meaningful user changes in the current checkout. Keep the demo scope; commit, push, provision, or deploy only when requested by the user. Read the matching role guide: [coder](agents/coder.md), [tester](agents/tester.md), [planning](agents/pm.md), or [deployer](agents/deployer.md).
