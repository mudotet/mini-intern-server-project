# Battle Pass design

Status: implemented and locally verified on 2026-10-23. Java unit/HTTP integration tests and the built Docker demo passed; live AWS remains unverified.

## Agreed scope

Implement the Java server APIs, Protobuf contracts, persisted state, and Swagger demo. Keep the existing handler → service → DAO flow. The React application remains Proto Studio. Real-money IAP, mission tracking, combat, and Hero upgrades are outside this feature.

## Progress and claims

- All Players share the same BattlePass definition and dates; each Player has independent progress and claims.
- One BattlePass contains many ordered nodes. Its node count comes from the configured sequence; ten nodes is only the demo example, not a fixed limit. A pass with N nodes requires N × 500 total BattlePassEXP to finish.
- Each pass has a mixed sequence of Free and Premium nodes. Every node requires its own full 500 BattlePassEXP before it is reached.
- Each node defines exactly one reward: one Resource type with a quantity, or one Hero. A node cannot bundle multiple Resource types or combine a Hero with a Resource reward.
- BattlePassEXP is separate from the Player XP Resource.
- EXP carries forward between nodes: 400/500 plus 110 reaches one node and leaves 10/500 toward the next. A single grant can reach multiple nodes.
- From zero, 499 EXP reaches no nodes; 500 reaches node 1; 510 leaves node 2 at 10/500; 1,000 reaches node 2. Partial progress never makes a node claimable.
- Reaching nodes is automatic and independent of claiming rewards. Unclaimed and Premium-locked nodes do not block progress.
- Claim one node per request. Any reached, eligible node can be claimed, regardless of earlier unclaimed nodes.
- Free nodes require only sufficient progress. Premium nodes additionally require PremiumAccess.
- A claim response includes the one actual reward granted: a Resource quantity, one newly owned Hero, or Gems replacing an already-owned Hero. It also includes updated Player state; that state is not an additional reward.
- Node rewards are selected by the server's definition, never by the claim request.
- Repeated claims cannot award rewards twice.
- Total progress is capped at the last node. Excess EXP is discarded and never transfers to another pass.

## Premium purchase

- One purchase costs Gems and unlocks every Premium node in that pass.
- Reaching nodes still requires EXP after purchase. Purchase itself does not grant node rewards.
- Buying Premium late makes previously reached Premium nodes claimable.
- Buying already-owned PremiumAccess must not charge Gems again.
- Insufficient Gems must leave all Player state unchanged.

## Dates and rollover

- Each pass has a distinct identity and a shared start/end interval.
- The demo repeats the same configured reward template in consecutive 30-day intervals, starting from a configured schedule origin.
- All Players observe the same active pass, regardless of when they log in.
- At expiration, old unclaimed rewards become unavailable. New progress starts at zero, with no claimed nodes and no PremiumAccess.
- Previously awarded Resources and owned Heroes persist across passes.
- No EXP from an expired pass carries forward.

Implementation defaults: use precise timestamps and intervals including the start and excluding the end. Derive the active pass from the configured origin and duration using the server Clock; no background reset worker is needed. Mutating requests identify their intended pass so delayed calls cannot affect a new pass accidentally. A retry of a previously committed operation can return its original receipt without performing another mutation.

## Quest integration

- BattlePass logic only receives Quest reward grants and updates progress. It does not track wins, mission eligibility, Quest schedules, or completion conditions.
- For Swagger, provide a demo-only endpoint accepting `quest_id`, `exp_bp`, and a unique `grant_id`, together with the intended BattlePass identity.
- This endpoint simulates a trusted Quest system. It must not become an ordinary production endpoint through which Players choose their own EXP.
- Quest reward responses return updated BattlePass progress; they do not automatically grant reached node rewards.
- The same Quest can produce multiple legitimate grants with different `grant_id` values. There is no once-per-Quest-per-pass restriction.
- Replaying the same grant cannot add EXP twice, even with a different HTTP RequestId. Reusing a grant identity with different input is rejected.
- Only positive EXP grants are valid. Grants targeting expired or inactive passes cannot add EXP to the current pass.

## Demo configuration

All values below are configurable shared definitions.

- Node interval: 500 BattlePassEXP.
- Premium price: 100 Gems per pass.
- Pass duration: 30 days.
- Demo node count: 10; completion requires 5,000 BattlePassEXP. Other configured passes can contain more nodes.

| Node | Required total EXP | Access | Reward |
| --- | --- | --- | --- |
| 1 | 500 | Free | 100 Gold |
| 2 | 1,000 | Premium | 500 Gold |
| 3 | 1,500 | Free | Basic Hero: Scout |
| 4 | 2,000 | Premium | Epic Hero: Knight |
| 5 | 2,500 | Free | 50 Player XP |
| 6 | 3,000 | Premium | Legendary Hero: Dragon |
| 7 | 3,500 | Free | 200 Gold |
| 8 | 4,000 | Premium | 200 Player XP |
| 9 | 4,500 | Free | 20 Gems |
| 10 | 5,000 | Premium | Legendary Hero: Phoenix |

## Heroes and compensation

- Heroes have predefined identities and rarity: Basic, Epic, or Legendary.
- Ownership persists across passes and is separate from pass progress.
- Claiming an unowned Hero grants ownership. Claiming an already-owned Hero grants Gems instead; compensation replaces that Hero reward and does not add a second reward.
- Compensation is determined by rarity: Basic = 10 Gems, Epic = 50 Gems, Legendary = 100 Gems.
- Premium nodes provide higher-rarity Heroes. No combat statistics or upgrade system are required for this demo.

## API responsibilities

| Action | Input | Result |
| --- | --- | --- |
| View BattlePass | Authenticated Player | Active definition, dates, Premium price, node rewards, progress, reached/claimed status, and owned Heroes |
| Purchase PremiumAccess | Pass identity and RequestId | Gems deduction and PremiumAccess, with updated Resources/progress |
| Claim node | Pass identity, node identity, and RequestId | One actual awarded reward and updated Resources, Heroes, and claims |
| Grant Quest reward, demo only | Pass identity, Quest identity, grant identity, EXP, and RequestId | Updated progress; no node rewards |

Implementation defaults: derive Player identity from the validated Session. Persist reward claims, Hero ownership changes, compensation, Premium payment, and grant deduplication atomically with their corresponding state updates. Preserve existing shop/login behavior and Player fields. Follow the project's existing RequestId retry rules; changed input cannot reuse an operation's identity. Exact route codes and Protobuf field names will follow existing source conventions during implementation.

## Implementation and verification order

Detailed implementation plan: [2026-10-23-battle-pass.md](superpowers/plans/2026-10-23-battle-pass.md).

1. Add shared definitions, Hero catalog, progress model, and Protobuf contracts.
2. Implement BattlePass view and season selection.
3. Implement demo Quest EXP grants with deduplication and overflow carry.
4. Implement Premium purchase with atomic Gems deduction.
5. Implement node claims, Hero ownership, and compensation.
6. Add Swagger examples and focused unit/HTTP integration coverage.

Required scenarios include rejecting nodes with zero or multiple configured rewards, exactly one granted reward per claim, 499/500/510/1,000 EXP boundaries, 400 + 110 EXP, grants reaching several nodes, the final-node cap, progress through locked/unclaimed nodes, late Premium purchase, insufficient Gems, out-of-order eligible claims, duplicate claims and Quest grants, concurrent mutations, duplicate Hero compensation, restart persistence, exact date boundaries, and season reset. Existing login/shop tests remain regression coverage. Local Redis/DynamoDB verification and live AWS verification must be reported separately.

## Delivery evidence

The implementation includes `BattlePassHandler`, `BattlePassPurchaseHandler`, `BattlePassClaimHandler`, and the explicitly enabled demo `BattlePassQuestGrantHandler`. See [TESTING.md](TESTING.md) for the 18-unit/27-integration result and ten-node Docker rehearsal. Template IDs are limited to 108 characters so generated season IDs always fit the 128-character request limit. The runtime uses a separate versioned Battle Pass item and transactional receipts; shop Player updates cannot erase progress or Hero ownership.
