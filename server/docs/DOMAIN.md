# Domain

[CONTEXT.md](../CONTEXT.md) is the glossary; [ROADMAP.md](ROADMAP.md) records demo rules.

Account owns one Player; Device links to Account; Session authenticates identity. InitialResources are granted in the Player creation transaction; Player Init only reads.

ShopPackage holds base price, Resource types, static quantities, and daily ranges. DailyOffer selects package, discount, and quantities once/player/cycle. Snapshot retains the results and counters in DynamoDB.

Purchase reads static configuration or a daily snapshot, debits gold/gem, and credits Resources. Player/version/counter and receipt are written atomically. Purchase RequestId is tied to Player and input; retries return the original result.

These flows have source code and local tests. Local verification passed 9 unit + 14 integration tests and the isolated Docker demo on 2026-10-02; live AWS EC2 remains unverified. The final ./gradlew clean compileJava test integrationTest build passed in 24 seconds. Guest login is for the demo only; the current implementation links one Device/Account. See [ARCHITECTURE.md](ARCHITECTURE.md) for the architecture.
