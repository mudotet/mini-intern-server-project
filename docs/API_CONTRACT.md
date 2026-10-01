# API contract

Các flow game dùng HTTP POST, Content-Type: application/x-protobuf hoặc application/json. Response dùng cùng định dạng với request. X-Request-Id được echo; Login/Purchase bắt buộc 16–128 ký tự chữ/số/underscore/hyphen, nên dùng UUID ngẫu nhiên. Các API thông tin dùng GET và luôn trả JSON.

## Swagger và JSON

GET /swagger/ là Swagger UI; GET /swagger/openapi.json là OpenAPI 3.0.3. Chọn application/json để dùng Try it out; copy access_token từ Login vào Authorize. Swagger/asset chạy cùng app và dùng file trong classpath.

JSON được parse bằng Protobuf JsonFormat rồi gọi handler/service hiện có. Field response giữ snake_case; int64 trả string và default value 0/false được in. Request rỗng có thể gửi {} hoặc body rỗng. Field JSON không có trong schema bị từ chối. [JsonFormat docs](https://protobuf.dev/reference/java/api-docs/com/google/protobuf/util/JsonFormat.html).

Giữ RequestId/input khi retry, kể cả đổi transport JSON ↔ Protobuf. Receipt lưu Protobuf, cùng Player vẫn chỉ mua một lần. Mỗi lượt mới tạo RequestId mới. API chọn response format theo Content-Type của request; Accept không chuyển định dạng riêng.

| Flow | Path | Request | Auth |
| --- | --- | --- | --- |
| Public init | /api/001003 | CommonInitRequest | Public |
| Login | /api/001002 | CommonLoginRequestProto | Public |
| Player Init | /api/002007 | PlayerInitRequestProto rỗng | Bearer JWT + Redis |
| Static | /api/003004 | Body rỗng | Bearer JWT + Redis |
| Daily | /api/003005 | Body rỗng | Bearer JWT + Redis |
| Purchase | /api/003002 | ShopPurchaseRequestProto | Bearer JWT + Redis |

## Thông tin để hiển thị

Nhóm Swagger **05 · Thông tin** gồm các API GET, luôn trả application/json và Cache-Control: no-store. Cần Bearer JWT + Session Redis, không cần Content-Type, body hoặc X-Request-Id. Body khác rỗng bị từ chối (400). Player lấy từ JWT đã xác thực; query player_id không chọn Player khác.

| Path | Response | Dùng để |
| --- | --- | --- |
| /api/info/player | PlayerProfileResponseProto | Hiển thị account_id/player_id |
| /api/info/resources | PlayerResourcesResponseProto | Resource hiện tại, đọc lại sau Purchase |
| /api/info/packages | ShopPackagesResponseProto | Package chung: giá gốc, rewards và khoảng daily quantity |
| /api/info/session | SessionInfoResponseProto | Account/Player của phiên, expires_at/server_time/remaining_seconds |

Player/Resource đọc consistent GetItem từ DynamoDB; Package lấy từ ShopCatalog đang dùng bởi ShopService. Không refill, không random DailyOffer và không ghi Player. Package không chứa điều kiện DailyOffer đã chọn; gọi API Daily để lấy giá/discount/reward/counter của ngày.

Session info kiểm tra chữ ký/hạn JWT và Session Redis; không trả access_token, device_id hoặc session_id. expires_at/server_time là Unix seconds; remaining_seconds đếm tới hạn JWT. Redis mất Session hoặc login mới thay thế Session thì API trả 401, dù JWT cũ chưa tới hạn. Các int64 vẫn là string trong JSON.

## Login và Init

Login device_id rỗng: server tạo Device ID ổn định theo RequestId, tạo Account/Player và tài nguyên mặc định. Retry phải giữ nguyên cả body và RequestId. Không đổi body thành Device ID vừa nhận khi retry request đầu tiên.

Device đã biết: gửi device_id; account_id/player_id tùy chọn, nếu có phải khớp. Dùng RequestId mới để tạo Session mới. Response có account_id, player_id, device_id, access_token, session_expires_at, new_account.

Login cache tồn tại tối đa 12 giờ trong Redis. Retry giữ Session cũ; nếu Session bị thay thế hoặc Redis mất Session, dùng RequestId mới để login. Identity/InitialResources vẫn chỉ tạo một lần.

Đây là guest login cho demo: người biết Device ID có thể phục hồi Account. Không có password, OAuth hoặc liên kết nhiều Device. Giữ Device ID như thông tin đăng nhập.

JWT gồm account_id/player_id/device_id/session_id và exp. Server kiểm tra chữ ký/expiry và so với Session Redis trước khi gọi service. Player ID cho các API được lấy từ JWT, không từ body.

Public init trả metadata/version/time. Player Init trả Resource hiện tại; không cấp lại tài nguyên.

## Shop và Purchase

Static offer ID: static:<package_id>. Daily: daily:<cycle_start>:<slot_id>:<package_id>, phạm vi Player do auth xác định.

Daily active khi cycle_start <= thời gian server < expires_at. Ba package khác nhau, giảm 10/20/30%, quantity trong khoảng cấu hình; giá = max(1, base × (100 − discount) / 100), chia nguyên. Giá/reward/discount/limit được lưu và giữ trong ngày. Mốc ngày: 00:00 Asia/Ho_Chi_Minh.

Purchase request id lấy từ shop response, amount=1. Server tự lấy giá/reward; không nhận giá từ client. items trong response là rewards vừa nhận, resources là tài nguyên sau lần mua, offer_id là ID đã mua.

Cùng (Player, RequestId, offer ID, amount) trả original response đã lưu, kể cả khi số dư hiện tại đã thay đổi hoặc offer đã hết hạn. Cùng RequestId với input khác trả 409. Receipt không hết hạn trong demo.

## Lỗi

BusinessErrorProto gồm code và message. Không có exception/stack trace trong response.

| HTTP | Code |
| --- | --- |
| 400 | INVALID_REQUEST, INVALID_JSON, INVALID_PROTOBUF, INVALID_IDENTITY, REQUEST_ID_REQUIRED, INVALID_PURCHASE |
| 401 | AUTH_INVALID |
| 404 | OFFER_NOT_FOUND, ROUTE_NOT_FOUND |
| 409 | IDENTITY_MISMATCH, REQUEST_ID_REUSED, INSUFFICIENT_FUNDS, OFFER_EXPIRED, PURCHASE_LIMIT, RETRY |
| 500 | INTERNAL_ERROR |

RETRY: gửi lại cùng body/RequestId. Unknown route trả 404. Xem [schema](../src/main/proto) và [script demo](../scripts/demo.sh) để encode/decode.
