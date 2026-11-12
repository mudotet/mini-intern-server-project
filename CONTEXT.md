# Mini Game Server

This glossary defines identity, session, player state, and shop language for the mini game backend.

## Language

**Account**:
The player-owned identity. An Account owns one Player identity. This demo links one Device to each Account.

**Device**:
A login client linked to an Account and used to recover its Account and Player during login.
_Avoid_: Client

**Player**:
The gameplay identity owned by an Account. A Player owns Resources.
_Avoid_: Account

**Session**:
A finite-lived authentication grant belonging to one Account, one Player, and one Device. A Device has at most one current Session.
_Avoid_: Token

**Resource**:
A currency or gameplay quantity owned by a Player. Gold and Gems are the currencies used to pay for Purchases.
_Avoid_: Balance

**XP**:
A Player's accumulated experience, awarded as a Resource reward.

**InitialResources**:
The default Resources assigned once to a new Player.

**ShopPackage**:
A shared product definition describing its base price, reward resource types, default reward quantities, and allowed daily reward quantity ranges. Each package keeps its resource types across StaticShop and DailyShop.

**ShopOffer**:
The terms under which a ShopPackage is available for purchase, including its price and any purchase limits.

**DailyOffer**:
A Player-specific ShopOffer whose discount and reward quantities are selected once for one daily cycle. Its discounted price and resolved rewards remain fixed until expiration, with a purchase limit for that cycle.

**StaticShop**:
A shared catalogue of ShopPackages sold at their base prices, available across days without daily purchase limits.

**DailyShop**:
A Player's daily selection of DailyOffers for distinct ShopPackages. A new selection replaces the expired offers when a new daily cycle begins; the underlying ShopPackage definitions remain shared.

**ShopSnapshot**:
The DailyOffers resolved for one Player and one daily cycle, including the selected prices, rewards, and purchase counts. Prices and rewards stay fixed within the cycle, while purchase counts change as the Player buys.

**Purchase**:
A Player's exchange of Gold or Gems for a ShopPackage's rewards under its applicable sale terms. Changes to the affected Resources are atomic.

**RequestId**:
A client-assigned identity for one retryable operation. Reuse is valid only when the operation input is identical.

**BattlePass**:
A shared weekly season with Player-specific cumulative EXPBP, Premium ownership, and claimed Milestones.

**EXPBP**:
Experience earned by manually claiming completed Battle Pass Quests.
It unlocks Milestones and remains separate from Player XP.

**Milestone**:
An EXPBP threshold with independent Free and Premium reward nodes.
Claiming a node grants its Resources once per season without consuming EXPBP.

**Premium**:
A once-per-season Gem purchase that permits claiming reached Premium reward nodes.

**BattlePassQuest**:
A daily objective whose progress comes from successful Shop Purchases.
Manually claiming a completed Quest grants EXPBP once for that day.

### Proto Tool

**ProtoSchema**:
The shared definition of messages, fields, and enums used by client and server.
Editing a ProtoSchema changes the protocol contract, not the game configuration values represented by that contract.

**ProtoRevision**:
The complete set of ProtoSchemas at one specific repository commit, identified by its full commit SHA.
A committed schema change produces a new ProtoRevision; editing or exporting files does not change the selected ProtoRevision.

**ProtoDraft**:
An editable set of ProtoSchemas based on a selected ProtoRevision.
A ProtoDraft can be exported without being committed to the source repository.
