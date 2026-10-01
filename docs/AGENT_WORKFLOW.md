# Workflow cho demo

1. Đọc [CONTEXT.md](../CONTEXT.md), [ROADMAP.md](ROADMAP.md), Git status và source của flow.
2. Chọn một hành vi nhỏ nhưng chạy trọn: request → handler → service → DAO → dữ liệu → response.
3. Sửa contract nếu cần, rồi triển khai class có nội dung cho flow đó.
4. Kiểm tra hành vi, compile/test và diff.
5. Ghi kết quả thực tế, phần chưa chạy và bước tiếp theo.

Dùng handler → service → DAO cụ thể. Domain model giữ state/quy tắc; mapping Protobuf thực hiện tại nơi dùng. Interface, abstract base, mapper riêng hoặc store adapter chỉ thêm khi có nhu cầu thật.

Redis giữ Session; DynamoDB lưu Account/Device/Player, daily snapshot và purchase receipt. Config package ban đầu là file đóng gói cùng app. Đích demo là một EC2, app + Redis và DynamoDB AWS.

Class/proto tồn tại chưa chứng minh endpoint hoạt động. Các flow chính có test HTTP với Redis/DynamoDB Local; AWS cần kiểm tra riêng.

Bảo toàn thay đổi có nội dung của người dùng trong checkout hiện tại. Giữ scope demo; commit, push, provision hoặc deploy khi đã được người dùng yêu cầu. Các hướng dẫn vai trò nằm trong [agents/README.md](agents/README.md).
