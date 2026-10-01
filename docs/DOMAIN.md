# Domain

[CONTEXT.md](../CONTEXT.md) là glossary; [ROADMAP.md](ROADMAP.md) ghi quy tắc demo.

Account sở hữu một Player; Device liên kết Account; Session xác thực identity. InitialResources được cấp trong transaction tạo Player, Player Init chỉ đọc.

ShopPackage giữ giá gốc, loại Resource, quantity static và daily range. DailyOffer chọn package, discount và quantity một lần/player/cycle. Snapshot giữ kết quả và counter, lưu trong DynamoDB.

Purchase đọc static config hoặc daily snapshot, trừ gold/gem và cộng Resource. Player/version/counter cùng receipt được ghi atomically. RequestId của purchase gắn với Player và input, retry trả kết quả cũ.

Đây là các flow đã có source và test local. Guest login chỉ phục vụ demo; triển khai hiện tại liên kết một Device/Account. Source kiến trúc nằm trong [ARCHITECTURE.md](ARCHITECTURE.md).
