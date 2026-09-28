# Mini Game Server

This context defines identity, session, player-state, and future commerce language for the mini game backend.

## Language

**Account**:
The player-owned identity. An Account owns one Player identity and may be linked to multiple Devices.

**Device**:
A login client linked to an Account and used to recover its Account and Player during login.
_Avoid_: Client

**Player**:
The gameplay identity owned by an Account. A Player owns Resources and InventoryItems.
_Avoid_: Account

**Session**:
A finite-lived authentication grant belonging to one Account, one Player, and one Device. A Device has at most one current Session.
_Avoid_: Token

**Resource**:
A currency or gameplay quantity owned by a Player.
_Avoid_: Balance

**InventoryItem**:
An item and quantity owned by a Player.
_Avoid_: Resource

**ShopOffer**:
Shared game configuration describing a purchasable package.

**ShopSnapshot**:
The offers resolved for one Player and one shop cycle.

**Purchase**:
A Player action that atomically changes Resources and InventoryItems in exchange for a ShopOffer.

**RequestId**:
A client-assigned identity for one retryable operation. Reuse is valid only when the operation input is identical.
