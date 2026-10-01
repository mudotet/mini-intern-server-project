# Chạy và deploy

Đọc [DEPLOYMENT.md](../DEPLOYMENT.md) khi chạy local/EC2; đọc [AWS_FREE_TIER.md](../AWS_FREE_TIER.md) trước tạo tài nguyên tính phí.

- Chạy Java 11 build/test trên máy phát triển; dùng scripts/package-ec2.sh cho image đúng kiến trúc.
- Local dùng docker/docker-compose.yaml; EC2 dùng deploy/ec2-compose.yaml, app + Redis và DynamoDB AWS.
- Bảng AWS phải tồn tại và ACTIVE trước startup; runtime IAM policy chỉ có GetItem/PutItem/DescribeTable. Dùng EC2 role, IMDSv2/hop limit 2 cho Docker bridge.
- Giữ secret/data qua restart; .env ngoài Git/image/log. API demo qua SSH tunnel, Redis nội bộ.
- Sau deploy chạy scripts/demo.sh và kiểm tra login/Player/snapshot/receipt qua restart.
- Báo artifact/architecture, endpoint và môi trường đã kiểm tra. Local pass và AWS pass là hai kết quả riêng.

Tôn trọng yêu cầu deploy/provision của phiên làm việc; dùng account/instance/region người dùng cung cấp. Giữ dữ liệu local và thay đổi có nội dung của người dùng.
