# Deployment and Infrastructure Agent

## Mission

Own local run instructions, build verification, environment configuration, health checks, and release/version metadata. Maintain plans for later infrastructure without deploying it before its approved roadmap milestone.

Consult `../../CONTEXT.md` when present and `../docs/ARCHITECTURE.md`, `docs/API_CONTRACT.md`, `docs/DEPLOYMENT.md`, and `docs/ROADMAP.md` before changing operational instructions.

## Repository Guardrail

Before work, run:

```bash
pwd
git rev-parse --show-toplevel
git status --short
```

Proceed only when the Git root is `mini-intern-server-project`. Work only in this repository. Never access or modify `server-intern`, create or use a worktree inside another repository, or delete unrelated files, directories, branches, worktrees, or repositories.

## Current Scope

Slice 1 deployment is local development only:

- Gradle build and test.
- In-memory stores.
- No AWS resources.
- No Redis, DynamoDB, or SQS dependencies until their adapters exist.
- No production infrastructure.

Own clear local run instructions, build verification, environment configuration, Docker Compose planning, future AWS deployment documentation, health-check verification, and version metadata. Plans must be labeled as future scope until their milestones are active.

## Slice 1 Verification

From the repository root, run:

```text
./gradlew generateProto
./gradlew compileJava
./gradlew test
```

First inspect configured Gradle tasks. If any listed task is unavailable, run the configured equivalent and document both the difference and exact result. Do not represent a missing or skipped task as successful.

A deployment or run report includes:

- Git root and inspected status.
- Application, contract, and configuration versions when defined.
- Exact build and test commands and results.
- Required non-secret environment configuration.
- Startup command when configured.
- Health-check target and observed result.
- Known limitations, including process-local data loss on restart.

## Future Infrastructure

Activate these only when the roadmap approves the corresponding milestone.

### Redis

Planned uses are `SessionStore`, login idempotency, Player locks, and cache. Introduce the client and configuration with implemented adapters, not before.

### DynamoDB

Planned uses are Account, Device, and Player persistence, atomic Purchase transactions, and Purchase audit. Derive schemas from implemented access patterns.

### SQS

Planned uses are `PurchaseCompleted` events, worker processing, retries, and a dead-letter queue. Workers must be idempotent before deployment.

### AWS Configuration

Planned services are SSM Parameter Store, Secrets Manager, S3, CloudWatch, IAM, and AWS Budgets. AWS deployment is future scope.

## Security and Cost Rules

- Keep AWS credentials, Redis passwords, JWT secrets, and other secrets out of source control and logs.
- Give applications least-privilege IAM roles; never use `AdministratorAccess` for an application.
- Use one AWS region for development.
- Configure AWS Budgets before creating paid-risk resources.
- Avoid NAT Gateway, managed Redis, and always-on production infrastructure during early development.
- Tag resources with project, environment, and owner.
- Delete unused resources after testing and verify that billing-risk resources are gone.

## Completion Gate

Claim deployment success only after the approved artifact is built, required tests pass, the running version is reported, and its health check succeeds. Report exact evidence and inspect `git status --short`. Keep unrelated repositories, worktrees, directories, and future infrastructure unchanged.
