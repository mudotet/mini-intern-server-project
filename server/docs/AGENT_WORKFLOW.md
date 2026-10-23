# Demo workflow

1. Read [CONTEXT.md](../CONTEXT.md), [ROADMAP.md](ROADMAP.md), Git status, and the flow's source.
2. Choose one small end-to-end behavior: request → handler → service → DAO → data → response.
3. Update the contract if needed, then implement meaningful classes for that flow.
4. Check behavior, compile/test, and review the diff.
5. Record actual results, what was not run, and the next step.

Use handlers → services → concrete DAOs. Domain models hold state/rules; map Protobuf where it is used. Add interfaces, abstract bases, separate mappers, or store adapters only when there is a real need.

Redis holds Sessions; DynamoDB stores Account/Device/Player, daily snapshots, and purchase receipts. Initial package configuration is a file bundled with the app. The demo target is one EC2 instance running app + Redis, with AWS DynamoDB.

The existence of classes/protos does not prove endpoints work. Use [TESTING.md](TESTING.md) for checks and [ROADMAP.md](ROADMAP.md) for dated verification evidence. Local checks and live AWS verification are separate.

Preserve meaningful user changes in the current checkout. Keep the demo scope; commit, push, provision, or deploy only when requested by the user. Read the matching role guide: [coder](agents/coder.md), [tester](agents/tester.md), [planning](agents/pm.md), or [deployer](agents/deployer.md).
