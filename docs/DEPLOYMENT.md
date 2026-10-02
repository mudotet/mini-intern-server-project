# Run locally and deploy to EC2

## Local Docker setup

Requires Java 11 + Docker Compose + Python 3; curl/protoc are needed for the demo script.

```bash
bash scripts/run-local.sh
docker compose -f docker/docker-compose.yaml logs app
bash scripts/demo.sh
# Include a local Docker app restart check:
DEMO_RESTART_LOCAL=1 bash scripts/demo.sh
```

run-local generates a random JWT_SECRET when .env lacks a valid secret, runs installDist, and builds the image. Other .env variables are preserved. Wait for the “Mini game API ready” log. .env is ignored by Git; retain this secret across restarts so JWTs remain usable.

Open http://localhost:8080/swagger/ for a browser demo. On EC2, open the same URL through an SSH tunnel; Swagger assets are bundled in the image.

To run only Redis/DynamoDB, then run the app on the host:

```bash
docker compose -f docker/docker-compose.yaml up -d
export JWT_SECRET="$(openssl rand -hex 32)"
./gradlew run
```

If the Docker app already occupies port 8080, stop the app service first or change HTTP_PORT for the host process.

| Variable | Default / purpose |
| --- | --- |
| JWT_SECRET | Required, at least 32 bytes |
| GAME_TABLE | mini_game, also the Redis namespace |
| HTTP_PORT | 8080 |
| REDIS_HOST/REDIS_PORT | localhost/6379; Compose sets host redis |
| DYNAMODB_MODE | local or aws |
| DYNAMODB_ENDPOINT | Local: http://localhost:8000; Compose: http://dynamodb:8000 |
| AWS_REGION | us-east-1; in AWS, use the table's region |

Local uses local/local credentials. AWS mode uses the default credential chain without an endpoint override. [AWS SDK docs](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html).

Startup prepares a table with an id String HASH key and provisioned 10 RCU/10 WCU. Existing data in login_devices is preserved; this version uses mini_game with actual Account/Player records. A Device that logged in only to the old version receives a new Account/Player in the new table; there is no migration.

## Prepare AWS

1. Check Free Plan/credit in Billing as described in [AWS_FREE_TIER.md](AWS_FREE_TIER.md).
2. Create a DynamoDB table named mini_game, with partition key id String and no sort key/GSI; provisioned 10 RCU/10 WCU, with autoscaling disabled for the demo. Wait for the table to become ACTIVE before starting the app.
3. In [runtime-policy.json](../deploy/runtime-policy.json), replace REGION/ACCOUNT_ID with your region/account, matching the table name to GAME_TABLE. Create an IAM role trusted by EC2, attach the policy, and attach an instance profile to EC2. Runtime uses GetItem/PutItem/DescribeTable; transactional Put is governed by PutItem permission. [IAM transactions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transaction-apis-iam.html).
4. Choose a Linux EC2 instance with approximately 1 GiB RAM from the types allowed by your account; select an architecture matching the image (the build script defaults to x86_64). Install Docker Engine and the Docker Compose plugin for the operating system.
5. Metadata: IMDS endpoint enabled, IMDSv2 required, response hop limit 2 because the app runs in a Docker bridge. [EC2 IMDS docs](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-instance-metadata-service.html).
6. The Security Group only needs SSH 22 from your IP for the tunnel demo below. The API binds to localhost; Redis publishes no port.

Create the table in Console or CloudShell after selecting the correct account/region:

```bash
aws dynamodb create-table --region REGION --table-name mini_game \
  --attribute-definitions AttributeName=id,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --billing-mode PROVISIONED \
  --provisioned-throughput ReadCapacityUnits=10,WriteCapacityUnits=10
aws dynamodb wait table-exists --region REGION --table-name mini_game
```

The runtime role has no CreateTable/DeleteTable permissions; the AWS table must be created beforehand. Do not put AWS access keys in the image/.env; the EC2 IAM role supplies temporary credentials.

## Package and transfer the app

On the development machine:

```bash
bash scripts/package-ec2.sh
# For ARM64 EC2, use: bash scripts/package-ec2.sh linux/arm64
scp -i /path/key.pem build/mini-game-server-ec2.tar.gz deploy/ec2-compose.yaml .env.example USER@EC2_IP:~/
```

Build Java on the development machine; EC2 only loads the image and does not run Gradle. ECR is not required.

On EC2, for the first installation:

```bash
mkdir -p ~/mini-game
mv ~/mini-game-server-ec2.tar.gz ~/ec2-compose.yaml ~/.env.example ~/mini-game/
cd ~/mini-game
docker load -i mini-game-server-ec2.tar.gz
umask 077
cp -n .env.example .env
chmod 600 .env
python3 - <<'PY'
from pathlib import Path
import re
import secrets
import shlex

path = Path(".env")
text = path.read_text()
pattern = r"(?m)^[ \t]*(?:export[ \t]+)?JWT_SECRET[ \t]*=(.*)$"
values = re.findall(pattern, text)
if not any("".join(shlex.split(value, comments=True)).strip() for value in values):
    line = "JWT_SECRET=" + secrets.token_hex(32)
    if values:
        text = re.sub(pattern, lambda match: line, text)
    else:
        text += ("\n" if text and not text.endswith("\n") else "") + line + "\n"
    path.write_text(text)
PY
nano .env
# Set AWS_REGION and GAME_TABLE to match Console; retain JWT_SECRET.
docker compose --env-file .env -f ec2-compose.yaml up -d
docker compose -f ec2-compose.yaml logs app
```

Install Python 3 on EC2 before running the secret-generation step. The Python command writes a random JWT_SECRET directly into .env without printing it, only when no nonblank existing value is present; existing nonblank values and other settings are preserved. Configure AWS_REGION and GAME_TABLE in [.env.example](../.env.example)'s copied .env. Retain the secret when updating the app; do not share .env contents or put secrets in demo logs.

EC2 Compose already sets DYNAMODB_MODE=aws and REDIS_HOST=redis; no DynamoDB Local endpoint is needed. AWS credentials come from the IAM role attached to EC2 ([AWS SDK guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/ec2-iam-roles.html)). ACCOUNT_ID is used in runtime-policy.json; IP/DNS, SSH user, and .pem file are used in scp/ssh. These details do not need to be added to .env.

On the development machine, open a tunnel in a separate terminal, then run the demo:

```bash
ssh -i /path/key.pem -N -L 8080:127.0.0.1:8080 USER@EC2_IP
bash scripts/demo.sh http://localhost:8080
```

Stop any local app using 8080 before opening the tunnel, or use another port and pass the corresponding URL. The script creates a new guest Player for each demo; it writes data and purchases using gold/gem.

## Restart and stop

```bash
docker compose --env-file .env -f ec2-compose.yaml restart app
docker compose --env-file .env -f ec2-compose.yaml down
```

Restarting the app preserves DynamoDB data and Redis Sessions. down preserves the named Redis volume; AWS data also remains. If Redis loses a Session, log in again; Player/receipts are not lost.

After the demo: stop/terminate EC2 as needed, check retained EBS and public IPv4/Elastic IP, and delete the table if the data is no longer needed. down only stops containers; it does not stop AWS billing. Check Billing to confirm.

**Local verification on 2026-10-02 passed: ./gradlew test integrationTest (9 unit + 14 integration tests), ./gradlew installDist, and the repair-validation Docker build. The image ran as nonroot UID 10001. A rebuilt isolated app on port 18080 returned Swagger HTTP 200 and passed scripts/demo.sh: JSON Login/Init/Static/Daily/Purchase, Protobuf purchase retry, and all four information APIs. Dedicated validation table/keys were cleaned; existing user containers were untouched. Live AWS EC2 has not been verified. The final ./gradlew clean compileJava test integrationTest build passed in 24 seconds.**
