# Lập kế hoạch

Đọc [CONTEXT.md](../../CONTEXT.md) và [ROADMAP.md](../ROADMAP.md). Mục tiêu là demo backend đủ để giải thích và chạy trên EC2 với Redis/DynamoDB.

Thứ tự: Login + Player Init → StaticShop → DailyShop → Purchase → Docker/AWS.

Mỗi task ghi flow cần hoàn thành, file cần đọc, kết quả request mong đợi và cách kiểm tra. Chia theo hành vi có thể chạy, giữ lượng công việc phù hợp thời gian người dùng học/làm.

- Phân biệt source đang chạy, contract đã chuẩn bị và tính năng đang đề xuất.
- Giữ handler → service → DAO; chọn phương án cụ thể, ít lớp và ít dependency.
- Chỉ hỏi về nghiệp vụ chưa rõ; tự kiểm tra những sự thật có trong repo.
- Account/Player, tài nguyên mặc định, random reward/discount và giới hạn daily theo roadmap.
- Hoàn thành tiêu chí của flow trước khi lên task phụ thuộc.
- Kết quả bàn giao gồm thay đổi, lệnh đã chạy, kết quả và việc còn thiếu.

Làm trong checkout hiện tại; bảo toàn thay đổi có nội dung của người dùng. Scope ngoài demo cần yêu cầu mới từ người dùng.
