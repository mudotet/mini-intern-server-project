# Chạy local và deploy EC2

## Local bằng Docker

Cần Java 11 + Docker Compose + Python 3; curl/protoc để chạy script demo.

```bash
bash scripts/run-local.sh
docker compose -f docker/docker-compose.yaml logs app
bash scripts/demo.sh
# Thêm kiểm tra restart app Docker local:
DEMO_RESTART_LOCAL=1 bash scripts/demo.sh
```

run-local tạo JWT_SECRET ngẫu nhiên khi .env thiếu secret hợp lệ, chạy installDist và build image. Các biến khác trong .env được giữ. Chờ log “Mini game API ready”. .env được Git ignore; giữ secret này qua restart để JWT còn dùng được.

Mở http://localhost:8080/swagger/ để demo bằng browser. Trên EC2, mở cùng URL qua SSH tunnel; Swagger assets được đóng gói trong image.

Chỉ chạy Redis/DynamoDB rồi chạy app trên host:

```bash
docker compose -f docker/docker-compose.yaml up -d
export JWT_SECRET="$(openssl rand -hex 32)"
./gradlew run
```

Nếu app Docker đang chiếm port 8080, dừng service app trước hoặc đổi HTTP_PORT khi chạy host.

| Biến | Mặc định / vai trò |
| --- | --- |
| JWT_SECRET | Bắt buộc, tối thiểu 32 bytes |
| GAME_TABLE | mini_game, cũng là namespace Redis |
| HTTP_PORT | 8080 |
| REDIS_HOST/REDIS_PORT | localhost/6379; Compose đặt host redis |
| DYNAMODB_MODE | local hoặc aws |
| DYNAMODB_ENDPOINT | Local: http://localhost:8000; Compose: http://dynamodb:8000 |
| AWS_REGION | us-east-1; AWS chọn cùng region với bảng |

Local dùng credentials local/local. AWS mode dùng default credential chain và không endpoint override. [AWS SDK docs](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html).

Startup chuẩn bị một bảng id String HASH, provisioned 10 RCU/10 WCU. Dữ liệu cũ trong bảng login_devices được giữ; bản này dùng mini_game với Account/Player thật. Device chỉ từng đăng nhập bản cũ sẽ được tạo Account/Player mới trong bảng mới; không có migration.

## Chuẩn bị AWS

1. Kiểm tra Free Plan/credit trong Billing theo [AWS_FREE_TIER.md](AWS_FREE_TIER.md).
2. Tạo bảng DynamoDB tên mini_game, partition key id String, không sort key/GSI; provisioned 10 RCU/10 WCU, tắt autoscaling cho demo. Để table ACTIVE trước chạy app.
3. Trong [runtime-policy.json](../deploy/runtime-policy.json), thay REGION/ACCOUNT_ID bằng region/account của bạn, tên bảng đúng GAME_TABLE. Tạo IAM role trusted service EC2, attach policy và gắn instance profile vào EC2. Runtime dùng GetItem/PutItem/DescribeTable; Put trong transaction được kiểm soát bởi PutItem permission. [IAM transactions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transaction-apis-iam.html).
4. Chọn EC2 Linux khoảng 1 GiB RAM, theo loại máy được account cho phép; chọn kiến trúc khớp image (mặc định script build x86_64). Cài Docker Engine và Docker Compose plugin theo hệ điều hành.
5. Metadata: IMDS endpoint enabled, IMDSv2 required, response hop limit 2 vì app chạy trong Docker bridge. [EC2 IMDS docs](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-instance-metadata-service.html).
6. Security Group chỉ cần SSH 22 từ IP của bạn cho cách demo qua tunnel dưới đây. API bind localhost; Redis không publish port.

Tạo bảng có thể làm trong Console hoặc CloudShell sau khi chọn đúng account/region:

```bash
aws dynamodb create-table --region REGION --table-name mini_game \
  --attribute-definitions AttributeName=id,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --billing-mode PROVISIONED \
  --provisioned-throughput ReadCapacityUnits=10,WriteCapacityUnits=10
aws dynamodb wait table-exists --region REGION --table-name mini_game
```

Runtime role không có CreateTable/DeleteTable; bảng AWS cần tạo trước. Không đặt AWS access key trong image/.env; EC2 IAM role cung cấp credentials tạm thời.

## Đóng gói và chuyển app

Ở máy phát triển:

```bash
bash scripts/package-ec2.sh
# ARM64 EC2 thì dùng: bash scripts/package-ec2.sh linux/arm64
scp -i /path/key.pem build/mini-game-server-ec2.tar.gz deploy/ec2-compose.yaml .env.example USER@EC2_IP:~/
```

Build Java ở máy phát triển; EC2 chỉ load image, không chạy Gradle. Không cần ECR.

Trên EC2, lần cài đầu tiên:

```bash
mkdir -p ~/mini-game
mv ~/mini-game-server-ec2.tar.gz ~/ec2-compose.yaml ~/.env.example ~/mini-game/
cd ~/mini-game
docker load -i mini-game-server-ec2.tar.gz
umask 077
cp -n .env.example .env
chmod 600 .env
openssl rand -hex 32
nano .env
# Paste secret vua tao vao JWT_SECRET; sua AWS_REGION va GAME_TABLE theo Console.
docker compose --env-file .env -f ec2-compose.yaml up -d
docker compose -f ec2-compose.yaml logs app
```

Trong [.env.example](../.env.example), chỉ cần điền 3 biến: JWT_SECRET, AWS_REGION và GAME_TABLE. Lệnh openssl tạo secret để bạn paste vào .env trên EC2. Giữ nguyên secret khi cập nhật app; không chia sẻ nội dung .env hoặc lưu secret vào log demo.

Compose EC2 đã đặt DYNAMODB_MODE=aws và REDIS_HOST=redis; không cần endpoint DynamoDB Local. AWS credentials lấy từ IAM role gắn vào EC2 ([AWS SDK hướng dẫn](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/ec2-iam-roles.html)). ACCOUNT_ID dùng trong runtime-policy.json; IP/DNS, SSH user và file .pem dùng trong scp/ssh. Các thông tin đó không cần thêm vào .env.

Ở máy phát triển, mở tunnel trong terminal riêng rồi chạy demo:

```bash
ssh -i /path/key.pem -N -L 8080:127.0.0.1:8080 USER@EC2_IP
bash scripts/demo.sh http://localhost:8080
```

Dừng app local đang dùng 8080 trước mở tunnel, hoặc dùng port khác và truyền URL tương ứng. Script tạo guest Player mới cho mỗi lần demo; có ghi dữ liệu và mua bằng gold/gem.

## Restart và dừng

```bash
docker compose --env-file .env -f ec2-compose.yaml restart app
docker compose --env-file .env -f ec2-compose.yaml down
```

Restart app giữ dữ liệu DynamoDB và Session Redis. down giữ named Redis volume; dữ liệu AWS vẫn còn. Redis mất Session thì login lại; Player/receipt không mất.

Sau demo: stop/terminate EC2 theo nhu cầu, kiểm tra EBS còn giữ và public IPv4/Elastic IP; xóa bảng nếu không cần dữ liệu. down chỉ dừng container, không dừng tính phí AWS. Xem Billing để xác nhận.

**Đã kiểm tra local; chưa có EC2/SSH hoặc AWS credentials trong môi trường này để kiểm tra cloud thật.**
