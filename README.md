# Mini game server

Backend demo dùng Java 11, Akka HTTP, Protobuf, Redis và DynamoDB. Các flow đã có xử lý: Login → Player Init → Static/Daily Shop → Purchase.

## Chạy local

Cần Java 11, Docker Compose, Python 3, curl và protoc. Trong thư mục repo:

```bash
bash scripts/run-local.sh
docker compose -f docker/docker-compose.yaml logs app
bash scripts/demo.sh
```

Script tạo JWT secret ngẫu nhiên trong .env nếu chưa có secret hợp lệ, build Java distribution và chạy app + Redis + DynamoDB Local. Chờ log “Mini game API ready” trước khi chạy demo. App ở http://localhost:8080; dữ liệu local nằm trong docker/data và được bỏ qua bởi Git.

## Swagger UI

Mở [http://localhost:8080/swagger/](http://localhost:8080/swagger/) để xem 10 endpoint và thử bằng JSON:

1. Tạo/Copy RequestId ở đầu trang, paste vào X-Request-Id của Login; Execute với body {}.
2. Copy access_token từ response → Authorize, paste token.
3. Gọi Player Init, Static và Daily; Purchase với offer ID đã nhận, amount=1.
4. Lượt mua mới dùng RequestId mới; retry giữ nguyên ID/input.

Swagger assets nằm trong app nên trang không cần CDN. JSON dùng cùng schema/handler/service với Protobuf; int64 (quantity/time) là string. Xem [API contract](docs/API_CONTRACT.md).

### 05 · Thông tin

Sau Login → Authorize, dùng các API GET dưới đây để lấy dữ liệu hiển thị. Không cần body hoặc RequestId; response luôn là JSON.

| API | Dữ liệu |
| --- | --- |
| GET /api/info/player | account_id, player_id của Player hiện tại |
| GET /api/info/resources | Resource gold/gem/XP mới nhất; gọi lại sau Purchase |
| GET /api/info/packages | Định nghĩa Package: giá gốc, reward mặc định, khoảng quantity daily |
| GET /api/info/session | Account/Player của phiên, expires_at, server_time, remaining_seconds |

Các API đọc không cấp lại tài nguyên hoặc tạo DailyOffer. API session không trả credentials; thời gian còn lại là hạn JWT, Session Redis có thể mất/bị thay thế sớm hơn.

## Đọc code

Request → handler → service → GameDao → DynamoDB/Redis → response.

- Login tạo Account/Device/Player và cấp 1.000 gold, 100 gem, 0 XP một lần.
- Session JWT có hạn 12 giờ, kiểm tra chữ ký/expiry và Session Redis.
- Static có 6 package giá/reward cố định.
- Daily có 3 package khác nhau/player/ngày; random quantity và discount 10/20/30%, giữ trong ngày; 3 lần mua/offer.
- Purchase cập nhật Player và receipt trong cùng DynamoDB transaction; retry trả receipt cũ.

## Test và deploy

```bash
./gradlew test
./gradlew integrationTest
bash scripts/package-ec2.sh
```

integrationTest cần Redis/DynamoDB Local đang chạy; dùng namespace riêng rồi dọn dữ liệu test. Bộ deploy EC2 nằm trong deploy/. AWS runtime dùng IAM role, không dùng credentials local. **AWS thật chưa được kiểm tra trong phiên triển khai này.**

Đọc [cách chạy/deploy](docs/DEPLOYMENT.md), [API](docs/API_CONTRACT.md), [kiến trúc](docs/ARCHITECTURE.md), [test](docs/TESTING.md), [roadmap](docs/ROADMAP.md) và [glossary](CONTEXT.md).
