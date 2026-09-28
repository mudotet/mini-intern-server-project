# Deployment

## Current Deployment Model

Slice 1 is a local, single-process learning system. The application uses in-memory stores; all Account, Device, Player, Session, and idempotency state is ephemeral and is lost when the process stops. This is appropriate for current development and testing, not production durability or horizontal scaling.

## Local Assumptions

- A Java 11 runtime is installed.
- The repository Gradle wrapper is used.
- No Redis, DynamoDB, SQS, LocalStack, or AWS account is required for Slice 1.
- Secrets are supplied outside source control when JWT signing is implemented.
- Only one application process owns the in-memory state during a run.

## Gradle Workflow

Use the wrapper from the repository root:

```bash
./gradlew clean test
./gradlew build
```

The exact application run task must be documented when the application plugin and entry point are configured. Until then, documentation must not claim that a specific Gradle run task exists.

## Running Slice 1 Locally

Once the application entry point is implemented:

1. Build and test with the Gradle wrapper.
2. Supply the required local JWT configuration through the implemented configuration mechanism.
3. Start one application process using its configured Gradle or packaged-JAR command.
4. Call the init, login, and player resource initialization endpoints in order.
5. Restarting the process resets all in-memory data.

The concrete startup command and configuration names are implementation details to record when they exist.

## Health Checks

The application should provide a lightweight health endpoint before deployment automation depends on it. For Slice 1, health means the HTTP server can accept requests and required in-memory components are initialized. Future infrastructure health should distinguish application readiness from the availability of external dependencies.

## Versioned Releases

Each release should identify the application version, contract version, and configuration version. A release must pass the Gradle build and test workflow, record compatible client expectations, and avoid silently changing a published Protobuf contract. Rollback must use a release compatible with stored data and active contracts.

## Future Migration

Future adapters may migrate:

- Accounts, Devices, and Players to DynamoDB.
- Sessions and idempotency results to Redis.
- Purchase events to SQS with a DLQ.

Application services and endpoints must remain unchanged; adapter configuration selects the backing implementation. Infrastructure dependencies are added only when their adapters are implemented.

Future local infrastructure may use Docker Compose, LocalStack, and DynamoDB Local. Future AWS development deployment may use Terraform and CloudWatch with S3, SSM Parameter Store, and Secrets Manager where required.

## AWS Scope

AWS deployment is explicitly **future scope**. Slice 1 does not require AWS resources, managed Redis, deployment automation, or production infrastructure. See [AWS_FREE_TIER.md](AWS_FREE_TIER.md) for future development guidance.
