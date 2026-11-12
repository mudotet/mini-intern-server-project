# Khởi động và dừng demo mobile 1.1.0

Chạy các lệnh từ thư mục project trên Mac.

```bash
cd /Users/tus/Study/Self_Study/intern_project/mini-intern-server-project
```

Bản mobile này dùng backend và schema trên nhánh `release/mobile-demo-2026-11-12`.
Khi clone mới, dùng nhánh này cùng các submodule.

```bash
git clone --branch release/mobile-demo-2026-11-12 --recurse-submodules https://github.com/mudotet/mini-intern-server-project.git
```

## Docker nằm ở đâu

| Vị trí | Vai trò |
| --- | --- |
| `docker/docker-compose.yaml` | Chạy backend, Redis, DynamoDB Local và hai giao diện quản trị |
| `docker/application/Dockerfile` | Đóng gói bản Java đã build thành image backend |
| `.dockerignore` | Giới hạn dữ liệu gửi vào Docker build |
| `server/build/install/mini-game-server/` | Bản Java được Dockerfile đưa vào image |
| `docker/data/dynamodb/` | Dữ liệu DynamoDB Local trên Mac |
| `docker/data/redis/` | Dữ liệu Redis trên Mac |
| Docker volume `mini-game-redisinsight` | Dữ liệu giao diện RedisInsight |
| `build/mini-game-server-1.1.0-arm64.tar.gz` | Image backend ARM64 đã xuất, khoảng 126 MB |
| `deploy/ec2-compose.yaml` | Cấu hình chạy trên EC2 |

Image đang dùng có tên `mini-game-server:1.1.0`.
Container backend có tên `docker-app-1` và sử dụng bảng `mini_mobile_demo`.
Ứng dụng iPhone chạy bằng Expo Go và gọi API của backend trong Docker.

## Mỗi lần mở lại

Mở Docker Desktop và đợi Docker Engine sẵn sàng.

```bash
open -a Docker
docker info
```

Khởi động các container đã tạo sẵn.
Lệnh `start` giữ cấu hình hiện có, gồm image, bảng dữ liệu, JWT secret và cổng LAN.

```bash
docker compose --env-file /dev/null -f docker/docker-compose.yaml --profile app start --wait
docker compose --env-file /dev/null -f docker/docker-compose.yaml --profile app ps
curl --fail http://127.0.0.1:8080/swagger/openapi.json > /dev/null
```

Nếu API vừa khởi động chưa sẵn sàng, đợi log `Mini game API ready` rồi chạy lại lệnh curl.

```bash
docker compose --env-file /dev/null -f docker/docker-compose.yaml --profile app logs --tail 30 app
```

Trong Terminal thứ hai, mở Metro cho Expo Go bằng Node.js 24.
Giữ Terminal này chạy trong lúc demo.

```bash
cd /Users/tus/Study/Self_Study/intern_project/mini-intern-server-project/mobile
npm exec --yes --package=node@24 -- node node_modules/expo/bin/cli start --lan --port 8081
```

Nếu vừa clone hoặc thiếu `node_modules`, chạy `npm ci` bằng Node.js 24 trước khi mở Metro.

Trên iPhone, kết nối cùng Wi-Fi với Mac, mở Expo Go rồi quét mã QR của Metro.
Trong Connection của ứng dụng, đặt địa chỉ API là `http://DIA_CHI_IP_MAC:8080`.
Lấy địa chỉ Wi-Fi của Mac bằng lệnh sau.

```bash
ipconfig getifaddr en0
```

Địa chỉ lúc viết hướng dẫn là `http://192.168.100.7:8080`.
Kiểm tra lại IP khi đổi mạng Wi-Fi.

| Dịch vụ | Địa chỉ trên Mac |
| --- | --- |
| Swagger | http://127.0.0.1:8080/swagger/ |
| DynamoDB Admin | http://127.0.0.1:8001 |
| RedisInsight | http://127.0.0.1:5540 |
| Metro | Cổng 8081, mã QR trong Terminal |

## Xem trên trình duyệt hoặc giả lập

Để xem bản web, mở thêm Terminal và chạy lệnh sau từ `mobile/`.
Bản export `dist/` hiện có trên Mac này đã chứa giao diện mới.
Nếu vừa clone hoặc thiếu `mobile/dist/`, chạy lệnh export bên dưới trước khi mở preview.

```bash
npm run preview
```

Mở http://127.0.0.1:8090 trên Mac.
Sau khi sửa giao diện, export lại web bằng Node.js 24 trước khi xem preview.

```bash
npm exec --yes --package=node@24 -- node node_modules/expo/bin/cli export --platform web
```

Để mở lại đúng giả lập iPhone 13 đã dùng trên Mac này, chạy từ Terminal sau khi Metro sẵn sàng.
Bản Xcode trên Mac này dùng ứng dụng DeviceHub để hiển thị giả lập.

```bash
xcrun simctl boot 5086F18A-7035-4F4C-B468-46622DE089EF
open -a /Applications/Xcode.app/Contents/Applications/DeviceHub.app
xcrun simctl openurl 5086F18A-7035-4F4C-B468-46622DE089EF exp://127.0.0.1:8081
```

Nếu thiết bị đã boot, bỏ qua lệnh boot rồi chạy hai lệnh còn lại.

## Khi container đã bị xóa hoặc cần build lại

Với image đã xuất, nạp lại image bằng lệnh sau.

```bash
docker load -i build/mini-game-server-1.1.0-arm64.tar.gz
```

Để build từ source mới, dùng Java 11 và chạy hai lệnh sau từ root project.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 11)"
./gradlew installDist
docker build -f docker/application/Dockerfile -t mini-game-server:1.1.0 -t mini-game-server:demo .
```

Tạo hoặc cập nhật container với đúng bảng dữ liệu demo và cổng truy cập qua Wi-Fi.
Lệnh này dùng JWT secret mới cho lần tạo container, client sẽ đăng nhập lại khi session cũ hết hiệu lực.
Resources và tiến độ Player nằm trong DynamoDB Local.

```bash
export JWT_SECRET="$(python3 -c 'import secrets; print(secrets.token_hex(32))')"
GAME_IMAGE=mini-game-server:1.1.0 GAME_TABLE=mini_mobile_demo GAME_BIND_ADDRESS=0.0.0.0 \
  docker compose --env-file /dev/null -f docker/docker-compose.yaml --profile app up -d
```

## Dừng sau khi demo

Nhấn `Ctrl+C` trong các Terminal đang chạy Metro và web preview.
Dừng các container bằng lệnh sau từ root project.

```bash
docker compose --env-file /dev/null -f docker/docker-compose.yaml --profile app stop
```

Tắt giả lập iPhone 13 nếu đang sử dụng.

```bash
xcrun simctl shutdown 5086F18A-7035-4F4C-B468-46622DE089EF
```

Dùng `stop` để lần sau có thể chạy lại bằng `start`.
Dữ liệu nằm trong các thư mục và volume nêu ở trên.
