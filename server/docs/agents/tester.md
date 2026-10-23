# Testing

Read [ROADMAP.md](../ROADMAP.md), the [API contract](../API_CONTRACT.md), and [TESTING.md](../TESTING.md). Verify the current flow's behavior.

- API tests use the routes used by Main, with real headers and content types.
- Login with the same Device retains identity; Player Init does not grant Resources again; invalid or expired JWT/Session is rejected.
- Static retains default prices/rewards. Daily has 3 distinct packages; rewards/discounts remain unchanged during the day and after restart.
- Daily permits 3 purchases and rejects the fourth. Insufficient funds/expired offers do not change state.
- Retry with the same RequestId/input does not debit/credit twice; competing requests cannot overspend currency or purchase allowance.
- Test day changes using Clock and randomness with a controllable test source.
- Generate and compile modified protos; exclude stale generated classes that could hide missing imports.
- Mock tests do not prove Redis/DynamoDB Local or AWS works; state the environment used.

Add tests for real business risks or regressions. Report commands, results, and verification limits; adjust tests/production code to the correct behavior. Work in the current checkout and preserve meaningful user changes.
