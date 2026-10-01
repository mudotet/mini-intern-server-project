# Proto cho demo

Nguồn ở [src/main/proto](../src/main/proto), generated Java trong build/generated. Sửa nguồn rồi chạy Gradle; generated file không commit.

| Nhóm | File | Vai trò |
| --- | --- | --- |
| Common API | common_init.proto, common_login.proto | Metadata và Account/Player/Session |
| Player API | player_init.proto | Resource của Player đã auth |
| Shop API | shop_static_response.proto, shop_daily_response.proto | Giá/reward và daily counters |
| Purchase API | shop_purchase_request.proto, shop_purchase_response.proto | Offer ID/amount, rewards và tài nguyên |
| Blueprint | shop.proto | Package, default rewards, daily ranges và cấu hình cycle |
| Model | resource.proto, shop.proto | Resource và persisted daily snapshot |
| Error | error.proto | BusinessErrorProto |

Có 11 file proto phục vụ runtime. Đã bỏ schema Player/Initial/Currency không được dùng: Player lưu bằng attributes DynamoDB, InitialResources đặt tại PlayerState, resource IDs được validate tại ShopCatalog. Không giữ schema chỉ vì tên giống domain concept.

Proto3 scalar có giá trị mặc định 0. Khi protoc decode ra text, quantity=0, purchased=0 hoặc remaining=0 có thể không được in; client vẫn đọc được giá trị 0.

ShopPackage.items giữ quantity static; daily_reward_ranges giữ cùng resource IDs và min/max inclusive. Offer.items là kết quả đã random; purchase không random lại.

Field bỏ trên message còn dùng được reserve name/number. Khi thay contract đang có consumer, kiểm tra cả client và server. Bộ API demo hiện dùng BusinessErrorProto cho lỗi thay vì AuthErrorProto cũ.

```bash
./gradlew clean generateProto compileJava test
```

Dùng clean sau khi xóa/đổi tên schema để bỏ generated class cũ. Script demo sử dụng protoc trực tiếp với proto nguồn; xem [API_CONTRACT.md](API_CONTRACT.md).
