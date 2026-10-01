# Roadmap demo

## Đã triển khai

Login → Player Init → Static → Daily → Purchase chạy với Redis + DynamoDB Local. Có image app, Compose local và bộ deploy EC2. Nghiệp vụ từ phiên grill đã được đưa vào code và test.

| Phần | Quy tắc |
| --- | --- |
| Identity | Một Device liên kết một Account; Account sở hữu một Player |
| Login | Tạo/recover identity, Session 12 giờ; retry cùng body/RequestId |
| InitialResources | Một lần: 1.000 gold, 100 gem, 0 XP |
| Player Init | Trả Resource hiện tại, không refill |
| Static | 6 package cố định, giá gốc, default rewards, không giới hạn daily |
| Daily | 3 package khác nhau/player/ngày; quantity random, giữ loại Resource theo package |
| Discount | Uniform 10/20/30%, chọn một lần mỗi offer/cycle |
| Limit | 3 lần/offer/ngày |
| Snapshot | Giữ giá/reward/discount trong ngày và qua restart; counter thay đổi khi mua |
| Reset | 00:00 Asia/Ho_Chi_Minh, tạo snapshot khi request gặp expiry |
| Purchase | amount=1; trừ tiền/cộng reward/counter/receipt cùng transaction |
| Retry | DynamoDB receipt theo Player/RequestId; input khác bị từ chối |

Ví dụ xp_gold giá 100 gold, thưởng static 100 XP; daily quantity 80–140 XP. Nếu chọn 120 XP và discount 30%, ba lượt đều trả 70 gold/nhận 120 XP; lượt thứ tư bị từ chối.

Package là cấu hình chung; DailyOffer là điều kiện bán đã chọn cho một Player/ngày. Loại Resource cố định, quantity đổi trong khoảng; không random giá/reward trong lúc purchase.

## Đã kiểm tra và việc còn lại

- Swagger có 10 endpoint; mục Thông tin thêm GET Player/Resource/Package/Session để hiển thị.
- Build: 9 unit + 14 test HTTP với Redis/DynamoDB Local pass.
- Các schema/file rỗng hoặc ngoài phạm vi đã dọn; chỉ tạo class có xử lý.
- Local Compose và script demo được dùng để kiểm tra bộ app.
- AWS mode dùng default credential chain; image EC2 dùng Java distribution đã build.
- Cần EC2/region/IAM và thông tin SSH để kiểm tra trên AWS thật. Chưa provision hoặc deploy cloud trong phiên này.

Người dùng có khoảng 2 tuần, 1 giờ/ngày và báo Free Plan $100 credit. Dùng thời gian còn lại để đọc từng flow, chạy lại demo và triển khai AWS; không cần thêm flow ngoài phạm vi.

## Thứ tự học/diễn tập

1. Đọc Main, BaseApiHandler, LoginHandler → LoginService → GameDao.
2. Login/Init và xem Device/Account/Player trong DynamoDB, Session TTL Redis.
3. Đọc shop.textproto, ShopCatalog và ShopService; phân biệt Package với DailyOffer.
4. Mua 3 lần, retry và xem Resource/counter/receipt.
5. Đọc PurchaseService và transaction/version trong GameDao; chạy integrationTest.
6. Restart app, kiểm tra dữ liệu; đọc test Clock để giải thích đổi ngày.
7. Theo [DEPLOYMENT.md](DEPLOYMENT.md), chạy trên EC2 và diễn tập.

Demo backend; giữ scope gold/gem/XP, không có IAP, UI game hoặc worker reset.
