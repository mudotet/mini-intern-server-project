# AWS cho demo basic

Một EC2 chạy app + Redis; DynamoDB AWS cùng region. Người dùng báo có Free Plan/$100 credit; chưa xác minh Billing của tài khoản.

Free Plan kết thúc khi hết credit hoặc sau 6 tháng, tùy mốc nào tới trước. Điều kiện tài khoản và credit còn lại xem trong Console. [AWS Free Tier FAQ](https://aws.amazon.com/free/free-tier-faqs/).

DynamoDB Standard có allowance provisioned 25 RCU/25 WCU và 25 GB; mức dùng của các bảng khác cũng cần tính. Bảng demo dùng provisioned 10 RCU/10 WCU, không autoscaling/GSI/streams/PITR trong cấu hình ban đầu. On-demand không sử dụng allowance RCU/WCU provisioned. [DynamoDB pricing](https://aws.amazon.com/dynamodb/pricing/).

- Build image ở máy phát triển. App JVM heap 256 MiB; Compose EC2 giới hạn app 512 MiB, Redis 128 MiB để phù hợp máy nhỏ.
- Chọn instance trong account/region cho phép; xem giá EC2, EBS và IPv4, không mặc định mọi máy “micro” đều miễn phí.
- Redis ở mạng Docker nội bộ; API qua SSH tunnel. EC2 IAM role cấp đúng quyền trên một bảng.
- Xem credit/Free Plan expiry và Billing trước/sau demo. Budget alert giúp theo dõi, không tự dừng tài nguyên.
- Stop EC2 vẫn có thể còn EBS; terminate cần kiểm tra volume và IP còn giữ. Xóa DynamoDB table khi dữ liệu không còn cần.

[Cách deploy](DEPLOYMENT.md) gồm policy và lựa chọn image architecture. Chưa provision AWS trong phiên này; local test không chứng minh account eligibility hay IAM.
