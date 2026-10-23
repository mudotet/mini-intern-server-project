# Running and deployment

Read [DEPLOYMENT.md](../DEPLOYMENT.md) for local/EC2 execution; read [AWS_FREE_TIER.md](../AWS_FREE_TIER.md) before creating billable resources.

- Run Java 11 build/tests on the development machine; use scripts/package-ec2.sh for an image with the correct architecture.
- Local uses docker/docker-compose.yaml; EC2 uses deploy/ec2-compose.yaml, app + Redis, and AWS DynamoDB.
- The AWS table must exist and be ACTIVE before startup; runtime IAM policy has only GetItem/PutItem/DescribeTable. Use an EC2 role, with IMDSv2/hop limit 2 for Docker bridge.
- Preserve secrets/data across restarts; keep .env outside Git/images/logs. Demo the API through an SSH tunnel; keep Redis internal.
- After deployment, run scripts/demo.sh; its optional local restart check verifies receipt equality and Session validity, not the full Player or daily snapshot.
- Report artifact/architecture, endpoint, and verified environment. Local pass and AWS pass are separate results.

Respect the session's deployment/provisioning request; use the account/instance/region supplied by the user. Preserve local data and meaningful user changes.
