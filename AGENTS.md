# Mini game server demo

Đọc CONTEXT.md cho thuật ngữ và docs/ROADMAP.md cho flow đang làm. Kiểm tra source và Git status trong checkout hiện tại trước khi chỉnh sửa.

## Cách làm

- Làm từng flow: handler → service → DAO → dữ liệu → response.
- Dùng Java 11, Akka HTTP, Protobuf, Redis và DynamoDB đang có trong build.
- Service có thể gọi DAO cụ thể; tạo interface/base/helper khi có nhu cầu thật.
- Tạo model/service/DAO khi triển khai flow cần chúng; mỗi file cần có nội dung và vai trò rõ.
- Giữ thay đổi trong mini-intern-server-project. Bảo toàn thay đổi có nội dung của người dùng; việc dọn khung rỗng đã được yêu cầu trong phiên này.
- Phạm vi demo: login, Player Init, static/daily shop, purchase và deploy EC2 + DynamoDB.
- Các flow Login/Player Init/shop/purchase đã triển khai; phân biệt test local với xác minh AWS thật.
- Xem docs/agents/README.md khi cần hướng dẫn theo vai trò.

## Agent skills

### Issue tracker

Issues are tracked in this repository's GitHub Issues. See docs/agents/issue-tracker.md.

### Triage labels

Triage uses the five canonical labels without aliases. See docs/agents/triage-labels.md.

### Domain docs

Domain documentation uses a single-context layout. See docs/agents/domain.md.
