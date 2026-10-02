# Protobuf for the demo

Sources are in [src/main/proto](../src/main/proto), with generated Java in build/generated. Edit sources, then run Gradle; do not commit generated files.

| Group | File | Purpose |
| --- | --- | --- |
| Common API | common_init.proto, common_login.proto | Metadata and Account/Player/Session |
| Player API | player_init.proto | Authenticated Player's Resources |
| Shop API | shop_static_response.proto, shop_daily_response.proto | Prices/rewards and daily counters |
| Purchase API | shop_purchase_request.proto, shop_purchase_response.proto | Offer ID/amount, rewards, and Resources |
| Blueprint | shop.proto | Packages, default rewards, daily ranges, and cycle configuration |
| Model | resource.proto, shop.proto | Resources and persisted daily snapshot |
| Error | error.proto | BusinessErrorProto |

11 proto files serve the runtime. Unused Player/Initial/Currency schemas have been removed: Player is stored as DynamoDB attributes, InitialResources are defined in PlayerState, and resource IDs are validated in ShopCatalog. Do not retain a schema solely because its name resembles a domain concept.

Proto3 scalars default to 0. When protoc decodes to text, quantity=0, purchased=0, or remaining=0 may not be printed; clients still read 0.

ShopPackage.items holds static quantities; daily_reward_ranges holds the same resource IDs with inclusive min/max values. Offer.items contains the randomized results; purchase does not randomize them again.

Reserve names/numbers of fields removed from messages still in use. When changing a contract with existing consumers, check both client and server. The demo API currently uses BusinessErrorProto instead of the old AuthErrorProto.

```bash
./gradlew clean generateProto compileJava test
```

Use clean after deleting/renaming schemas to remove old generated classes. The demo script uses protoc directly with source protos; see [API_CONTRACT.md](API_CONTRACT.md).
