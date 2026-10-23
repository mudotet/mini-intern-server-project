# Demo roadmap

## Implemented

Login → Player Init → Static → Daily → Purchase are implemented for Redis + DynamoDB Local. An app image, local Compose setup, and EC2 deployment bundle are included. Business rules from the grilling session have been incorporated into code and tests. Local execution was verified on 2026-10-02; live AWS EC2 remains unverified.

| Area | Rule |
| --- | --- |
| Identity | One Device links to one Account; Account owns one Player |
| Login | Create/recover identity, 12-hour Session; retry with the same body/RequestId |
| InitialResources | Once: 1,000 gold, 100 gem, 0 XP |
| Player Init | Return current Resources, without refilling |
| Static | 6 fixed packages, base prices, default rewards, no daily limit |
| Daily | 3 distinct packages/player/day; random quantities, retaining each package's Resource types |
| Discount | Uniform 10/20/30%, selected once per offer/cycle |
| Limit | 3 purchases/offer/day |
| Snapshot | Retain price/reward/discount during the day and across restarts; counters change on purchase |
| Reset | 00:00 Asia/Ho_Chi_Minh; create a snapshot when a request encounters expiry |
| Purchase | amount=1; debit currency/credit reward/update counter/write receipt in one transaction |
| Retry | DynamoDB receipt keyed by Player/RequestId; different input is rejected |

Example: xp_gold costs 100 gold and grants 100 XP in static; daily quantities range from 80–140 XP. If 120 XP and a 30% discount are selected, all three purchases cost 70 gold/grant 120 XP; the fourth is rejected.

Packages are shared configuration; DailyOffer is the selected sale terms for one Player/day. Resource types are fixed, while quantities vary within their ranges; prices/rewards are not randomized during purchase.

## Implemented extension: Battle Pass

The ten-node Battle Pass is implemented with `BattlePassHandler`, Premium purchase, single-node claims, and a demo-only Quest EXP grant handler. See [BATTLE_PASS.md](BATTLE_PASS.md) for the rules. Local verification on 2026-10-23 passed 18 unit tests and 27 HTTP integration tests using real Redis/DynamoDB Local, the Java distribution build, and the isolated Docker ten-node demo. Live AWS remains unverified.

## Actual local verification and remaining work

- Swagger documents 14 endpoints, including the optional demo Quest grant API. The server registers 13 normally and 14 when that demo route is explicitly enabled.
- ./gradlew test integrationTest passed 9 unit + 14 HTTP integration tests with Redis/DynamoDB Local on 2026-10-02. The final ./gradlew clean compileJava test integrationTest build passed in 24 seconds.
- Empty or out-of-scope schemas/files have been removed; create only classes with meaningful behavior.
- ./gradlew installDist and the repair-validation Docker build passed; the image ran as nonroot UID 10001. The rebuilt isolated app on port 18080 returned Swagger HTTP 200 and passed JSON Login/Init/Static/Daily/Purchase, Protobuf purchase retry, and all four information APIs through scripts/demo.sh. Dedicated validation table/keys were cleaned; existing user containers were untouched.
- AWS mode uses the default credential chain; the EC2 image uses a built Java distribution.
- EC2/region/IAM and SSH details are needed for live AWS verification. No cloud resources have been provisioned or deployed in this session.

The original demo planning assumed approximately 2 weeks at 1 hour/day and a reported Free Plan with $100 credit. Battle Pass is a subsequently requested extension; the original deployment work remains separate.

## Learning/rehearsal order

1. Read Main, BaseApiHandler, LoginHandler → LoginService → GameDao.
2. Run Login/Init and inspect Device/Account/Player in DynamoDB and Session TTL in Redis.
3. Read shop.textproto, ShopCatalog, and ShopService; distinguish Package from DailyOffer.
4. Purchase 3 times, retry, and inspect Resources/counter/receipt.
5. Read PurchaseService and GameDao transaction/version handling; run integrationTest.
6. Restart the app and check data; read Clock tests to explain day changes.
7. Follow [DEPLOYMENT.md](DEPLOYMENT.md), run on EC2, and rehearse.

Backend demo only. The Battle Pass extension adds Hero ownership and compensation alongside Gold/Gems/XP. Real-money IAP, a game UI, mission tracking, and a background reset worker remain outside scope.
