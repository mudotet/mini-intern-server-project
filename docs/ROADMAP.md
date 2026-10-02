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

## Actual local verification and remaining work

- Swagger has 10 endpoints; the Information section adds GET Player/Resource/Package/Session for display.
- ./gradlew test integrationTest passed 9 unit + 14 HTTP integration tests with Redis/DynamoDB Local on 2026-10-02. The final ./gradlew clean compileJava test integrationTest build passed in 24 seconds.
- Empty or out-of-scope schemas/files have been removed; create only classes with meaningful behavior.
- ./gradlew installDist and the repair-validation Docker build passed; the image ran as nonroot UID 10001. The rebuilt isolated app on port 18080 returned Swagger HTTP 200 and passed JSON Login/Init/Static/Daily/Purchase, Protobuf purchase retry, and all four information APIs through scripts/demo.sh. Dedicated validation table/keys were cleaned; existing user containers were untouched.
- AWS mode uses the default credential chain; the EC2 image uses a built Java distribution.
- EC2/region/IAM and SSH details are needed for live AWS verification. No cloud resources have been provisioned or deployed in this session.

The user has approximately 2 weeks, 1 hour/day, and reported a Free Plan with $100 credit. Use the remaining time to read each flow, rerun the demo, and deploy to AWS; no additional out-of-scope flows are needed.

## Learning/rehearsal order

1. Read Main, BaseApiHandler, LoginHandler → LoginService → GameDao.
2. Run Login/Init and inspect Device/Account/Player in DynamoDB and Session TTL in Redis.
3. Read shop.textproto, ShopCatalog, and ShopService; distinguish Package from DailyOffer.
4. Purchase 3 times, retry, and inspect Resources/counter/receipt.
5. Read PurchaseService and GameDao transaction/version handling; run integrationTest.
6. Restart the app and check data; read Clock tests to explain day changes.
7. Follow [DEPLOYMENT.md](DEPLOYMENT.md), run on EC2, and rehearse.

Backend demo only; keep scope to gold/gem/XP, with no IAP, game UI, or reset worker.
