# Kiểm tra demo

Dùng Java 11:

```bash
./gradlew test
docker compose -f docker/docker-compose.yaml up -d
./gradlew integrationTest
```

test chạy 9 unit/route test: HTTP/content type/request ID/auth hook, Swagger HTML/OpenAPI/assets, Resource Init không refill, daily ranges/discount/Vietnam midnight và validation mua.

integrationTest chạy 14 test qua HTTP socket thật với Redis + DynamoDB Local:

- Login retry/recovery, Account ownership, JWT giả/hết hạn, Session bị thay thế/mất.
- Login đồng thời chỉ tạo một identity và cấp tài nguyên một lần.
- Daily đọc đồng thời giữ cùng snapshot; static ổn định.
- Daily mua 3 lần, lần 4 từ chối; giá/reward/discount giữ nguyên.
- Replay receipt không ghi state lần hai; RequestId/input khác bị từ chối.
- Request cạnh tranh lượt cuối hoặc số gold cuối chỉ một thành công.
- Bốn request mua giống nhau chỉ ghi một lần.
- Static mua hơn 3 lần; thiếu tiền và amount sai bị từ chối; Init không refill.
- Clock tới nửa đêm tạo cycle mới; offer cũ không mua được.
- Restart HTTP server/handler/DAO giữ identity, Resource, snapshot và receipt.
- JSON Login → Init → Shop → Purchase, lỗi JSON/auth/conflict và replay cùng receipt qua JSON/Protobuf.
- GET thông tin đọc đúng Player hiện tại, Resource sau mua và Package; không ghi version/snapshot, không chọn Player khác qua query.
- GET yêu cầu JWT/Session hợp lệ, không nhận body hoặc POST; GET không thể mua.
- Session info đếm thời gian bằng Clock; Session cũ/bị mất hoặc JWT hết hạn trả 401, không trả credentials.

Mỗi test tạo bảng mini_test_<UUID> và prefix Redis riêng, rồi dọn đúng namespace đó. Không dùng bảng GAME_TABLE của app. Có thể đổi IT_DYNAMODB_ENDPOINT, IT_REDIS_HOST/PORT cho môi trường test local.

Đã chạy build integrationTest installDist ngày 2026-09-30: 9 unit + 14 integration pass sau khi thêm API thông tin, không skipped. Đây là DynamoDB Local, không chứng minh IAM/throttling/EC2/DynamoDB AWS.

[demo.sh](../scripts/demo.sh) gọi app đang chạy và in giá/reward/tài nguyên, không in JWT. Dùng DEMO_RESTART_LOCAL=1 bash scripts/demo.sh để restart app Docker local và so sánh Player/snapshot/receipt/Session trước và sau. Đổi ngày kiểm tra bằng Clock trong test; không có endpoint cheat.
