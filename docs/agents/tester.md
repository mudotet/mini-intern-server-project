# Kiểm tra

Đọc [ROADMAP.md](../ROADMAP.md), [API contract](../API_CONTRACT.md) và [TESTING.md](../TESTING.md). Kiểm tra hành vi của flow đang làm.

- API test đi qua routes mà Main dùng, có header và content type thật.
- Login cùng Device giữ identity; Player Init không cấp lại tài nguyên; JWT/Session sai hoặc hết hạn bị từ chối.
- Static giữ giá/reward mặc định. Daily có 3 package khác nhau; reward/discount giữ nguyên trong ngày và sau restart.
- Daily mua 3 lần, lần 4 bị từ chối. Thiếu tiền/offer hết hạn không đổi state.
- Retry cùng RequestId/input không trừ/cộng lần hai; request cạnh tranh không tiêu quá tiền/lượt.
- Kiểm tra đổi ngày bằng Clock, random bằng nguồn có thể kiểm soát trong test.
- Proto sửa phải generate và compile; loại trừ generated class cũ làm che import thiếu.
- Test với mock không chứng minh Redis/DynamoDB Local hoặc AWS hoạt động; ghi rõ môi trường đã dùng.

Thêm test cho rủi ro nghiệp vụ hoặc regression thật. Báo lệnh, kết quả và giới hạn kiểm tra; chỉnh test/production code theo hành vi đúng. Làm trong checkout hiện tại và giữ các thay đổi có nội dung của người dùng.
