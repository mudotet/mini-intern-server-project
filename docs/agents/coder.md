# Coder

Đọc [CONTEXT.md](../../CONTEXT.md), [ROADMAP.md](../ROADMAP.md) và [API contract](../API_CONTRACT.md). Làm một flow đến khi chạy được rồi mới sang flow tiếp.

- Handler parse request, gọi service và trả Protobuf; service xử lý nghiệp vụ; DAO đọc/ghi DynamoDB. Redis giữ Session.
- Dùng class/constructor đơn giản. Service được gọi DAO cụ thể. Gom mapping vào nơi dùng; chỉ tách mapper/interface/helper khi giảm lặp thật.
- Tạo model, service và DAO có xử lý khi flow cần; tránh dựng khung cho các flow chưa làm.
- Player identity lấy từ JWT/Session đã kiểm tra. Tài nguyên được cấp trong transaction tạo Player; Player Init chỉ đọc.
- Daily random package, quantity và discount một lần, lưu snapshot. Purchase dùng kết quả đã lưu và kiểm tra giới hạn/expiry.
- Purchase phải ghi tiền, reward, lượt mua và receipt cùng giao dịch; retry không thay đổi state lần hai.
- Sửa proto nguồn, giữ field numbers của contract đang dùng; sinh Java bằng Gradle.
- Sau chỉnh sửa chạy compile/test phù hợp, đọc diff và báo đúng phần đã chạy. Test mock khác test Redis/DynamoDB thật.

Làm trong checkout hiện tại của mini-intern-server-project; xem Git status trước khi sửa. Giữ thay đổi có nội dung của người dùng và không sửa server-intern. Phạm vi theo roadmap; dependency thêm mới phải phục vụ flow đang làm.
