# Read the backend demo

1. [CONTEXT.md](../CONTEXT.md): Package, DailyOffer, Player.
2. [ROADMAP.md](ROADMAP.md): implemented rules and learning order.
3. [ARCHITECTURE.md](ARCHITECTURE.md): request → handler → service → GameDao.
4. [API_CONTRACT.md](API_CONTRACT.md), [PROTO_GUIDE.md](PROTO_GUIDE.md): APIs and schemas.
5. [TESTING.md](TESTING.md): unit tests and HTTP tests with Redis/DynamoDB Local.
6. [DEPLOYMENT.md](DEPLOYMENT.md), [AWS_FREE_TIER.md](AWS_FREE_TIER.md): local and EC2 setup.
7. Role-specific guidance: [coder](agents/coder.md), [tester](agents/tester.md), [planning](agents/pm.md), and [deployer](agents/deployer.md).

The main flows are implemented. Local verification passed 9 unit + 14 integration tests and the isolated Docker demo on 2026-10-02. Live AWS EC2 remains unverified; the final ./gradlew clean compileJava test integrationTest build passed in 24 seconds.
