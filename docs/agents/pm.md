# Planning

Read [CONTEXT.md](../../CONTEXT.md) and [ROADMAP.md](../ROADMAP.md). The goal is a backend demo that can be explained and run on EC2 with Redis/DynamoDB.

Order: Login + Player Init → StaticShop → DailyShop → Purchase → Docker/AWS.

Each task records the flow to complete, files to read, expected request results, and verification method. Split work by runnable behavior and keep it within the user's learning/working time.

- Distinguish working source, prepared contracts, and proposed features.
- Keep handler → service → DAO; choose concrete solutions with few layers and dependencies.
- Ask only about unclear business rules; inspect facts already available in the repository yourself.
- Follow the roadmap for Account/Player, default Resources, random rewards/discounts, and daily limits.
- Complete the flow's criteria before scheduling dependent tasks.
- Handoffs include changes, commands run, results, and remaining work.

Work in the current checkout and preserve meaningful user changes. Work outside the demo scope requires a new user request.
