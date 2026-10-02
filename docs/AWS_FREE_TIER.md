# AWS for a basic demo

One EC2 instance runs the app + Redis; AWS DynamoDB is in the same region. The user reported a Free Plan/$100 credit; account Billing has not been verified.

Free Plan ends when credit runs out or after 6 months, whichever comes first. Check account eligibility and remaining credit in Console. [AWS Free Tier FAQ](https://aws.amazon.com/free/free-tier-faqs/).

DynamoDB Standard has a provisioned allowance of 25 RCU/25 WCU and 25 GB; usage by other tables also counts. The demo table uses provisioned 10 RCU/10 WCU, with no autoscaling/GSI/streams/PITR in the initial configuration. On-demand does not use the provisioned RCU/WCU allowance. [DynamoDB pricing](https://aws.amazon.com/dynamodb/pricing/).

- Build the image on the development machine. The app JVM heap is 256 MiB; EC2 Compose limits the app to 512 MiB and Redis to 128 MiB to suit a small instance.
- Choose an instance allowed by the account/region; check EC2, EBS, and IPv4 pricing rather than assuming every “micro” instance is free.
- Redis stays on the internal Docker network; the API is accessed through an SSH tunnel. The EC2 IAM role grants the required permissions on one table.
- Check credit/Free Plan expiry and Billing before/after the demo. Budget alerts aid monitoring but do not automatically stop resources.
- Stopping EC2 may retain EBS; after termination, check retained volumes and IPs. Delete the DynamoDB table when its data is no longer needed.

[Deployment instructions](DEPLOYMENT.md) include the policy and image architecture choice. AWS has not been provisioned in this session; local tests do not establish account eligibility or IAM behavior. Local verification passed 9 unit + 14 integration tests and the isolated Docker demo on 2026-10-02; live AWS EC2 remains unverified. The final ./gradlew clean compileJava test integrationTest build passed in 24 seconds.
