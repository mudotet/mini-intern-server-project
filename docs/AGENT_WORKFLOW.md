# Agent Workflow

## Repository Safety

Every agent must verify the current repository before work:

```bash
pwd
git rev-parse --show-toplevel
git status --short
```

Agents work only inside the current `mini-intern-server-project` repository. No agent may access, modify, or create files in `server-intern`. Agents must not create a worktree inside another repository or delete unrelated files, directories, branches, worktrees, or repositories.

## Roles

### PM

- Define the milestone outcome and confirm it belongs to the roadmap.
- Keep current Slice 1 work separate from future shop, purchase, infrastructure, and AWS scope.
- Provide acceptance criteria, dependencies, constraints, and authoritative contract references.
- Resolve scope conflicts before handing work to the Coder.

### Coder

- Implement only the accepted milestone scope.
- Follow domain, architecture, API, and Protobuf boundaries.
- Add or update tests with behavioral changes.
- Avoid unrelated refactoring and unrequested infrastructure dependencies.
- Record changed files and verification commands for the Tester.

### Tester

- Review acceptance criteria and affected contracts independently.
- Run relevant unit, integration, API, contract, negative, and security tests.
- Reproduce failures with exact commands and concise evidence.
- Reject completion claims when tests were skipped, behavior is undocumented, or scope was exceeded.

### Deployer

- Deploy only an approved, versioned artifact after required tests pass.
- Confirm configuration and rollback expectations without exposing secrets.
- Verify health and expected Slice 1 endpoints after deployment.
- Treat Docker Compose, external stores, and AWS deployment as future scope until their milestones are approved.

## Handoff Rules

Every handoff includes:

- Repository path and verified Git root.
- Milestone and scope statement.
- Dependencies and acceptance criteria.
- Changed files.
- Commands executed and their results.
- Known limitations or failures.
- Explicit future-scope items that were not implemented.

The recipient re-verifies the repository and does not rely on an unverified claim from another agent.

## Scope Control

Implementation starts with Slice 1 only: init, idempotent login, and authenticated player resource initialization. Static shop, daily shop, purchase, Redis, DynamoDB, SQS, AWS deployment, and worker processing require their later roadmap milestones. Infrastructure dependencies are introduced only with corresponding adapters.

If a requested change crosses a milestone boundary, stop that portion and return it to the PM for explicit scope definition. Do not prepare speculative scaffolding.

## Definition of Done

Work is done only when:

- Acceptance criteria are satisfied.
- Required behavior and security tests pass.
- Contract and documentation changes are consistent.
- Formatting, linting, compilation, and tests configured by the repository pass.
- No unrelated files or repositories were changed.
- Current functionality is not described as future, and future functionality is not described as current.
- The handoff records exact verification evidence.

## Verification Before Completion

Before claiming completion, an agent must:

1. Re-run repository path and Git-root checks.
2. Inspect `git status --short` and the full diff.
3. Confirm changes are limited to the assigned scope.
4. Run the repository's applicable build, test, lint, and contract checks.
5. Verify documentation links and generated artifacts where relevant.
6. Report failures honestly; absence of verification is not success.
7. Avoid committing unless explicitly requested.
