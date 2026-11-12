package com.game.server;

import com.game.server.proto.BattlePassContract.BattlePassSnapshotProto;
import com.game.server.proto.ShopModel.ShopDailySnapshotProto;
import com.game_server.general.dao.GameDao.Player;
import com.game_server.general.service.BattlePassService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BattlePassServiceTest {
    private static final long MONDAY = Instant.parse("2026-11-08T17:00:00Z").getEpochSecond();

    private Player player(BattlePassSnapshotProto pass) {
        return new Player("account", "player", 8, Map.of("gold", 90L, "gem", 60L, "xp", 240L),
                ShopDailySnapshotProto.getDefaultInstance(), pass);
    }

    @Test
    void legacyPlayerStartsWithZeroPassAndRecordsOnlyThePurchasedRewards() {
        Player legacy = new Player("account", "player", 0, Map.of("gold", 1000L), ShopDailySnapshotProto.getDefaultInstance());
        BattlePassSnapshotProto result = BattlePassService.applyPurchase(legacy, Map.of("xp", 150), MONDAY + 100);
        assertEquals(MONDAY, result.getSeasonStart());
        assertEquals(MONDAY, result.getQuestDayStart());
        assertEquals(0, result.getExpbp());
        assertFalse(result.getPremiumOwned());
        assertEquals(1, result.getDailyPurchases());
        assertEquals(150, result.getDailyPurchasedXp());
        assertEquals(Map.of("gold", 1000L), legacy.resources);
        assertEquals(BattlePassSnapshotProto.getDefaultInstance(), legacy.battlePass);
    }

    @Test
    void dailyRolloverRetainsPremiumExpbpAndSeasonClaims() {
        BattlePassSnapshotProto prior = BattlePassSnapshotProto.newBuilder().setSeasonStart(MONDAY).setQuestDayStart(MONDAY)
                .setExpbp(350).setPremiumOwned(true).addClaimedNodes("free_1").addClaimedQuests("study")
                .setDailyPurchases(3).setDailyPurchasedXp(200).build();
        BattlePassSnapshotProto result = BattlePassService.applyPurchase(player(prior), Map.of("gold", 100), MONDAY + 86400);
        assertEquals(350, result.getExpbp());
        assertTrue(result.getPremiumOwned());
        assertEquals(prior.getClaimedNodesList(), result.getClaimedNodesList());
        assertEquals(0, result.getClaimedQuestsCount());
        assertEquals(1, result.getDailyPurchases());
        assertEquals(0, result.getDailyPurchasedXp());
        assertEquals(MONDAY + 86400, result.getQuestDayStart());
    }

    @Test
    void weeklyRolloverResetsPassAndProgressCapsAtQuestTargets() {
        BattlePassSnapshotProto prior = BattlePassSnapshotProto.newBuilder().setSeasonStart(MONDAY).setQuestDayStart(MONDAY + 6 * 86400)
                .setExpbp(600).setPremiumOwned(true).addClaimedNodes("premium_6").addClaimedQuests("trade_once")
                .setDailyPurchases(3).setDailyPurchasedXp(200).build();
        BattlePassSnapshotProto result = BattlePassService.applyPurchase(player(prior), Map.of("xp", 150), MONDAY + 7 * 86400);
        assertEquals(MONDAY + 7 * 86400, result.getSeasonStart());
        assertEquals(0, result.getExpbp());
        assertFalse(result.getPremiumOwned());
        assertEquals(0, result.getClaimedNodesCount());
        assertEquals(0, result.getClaimedQuestsCount());
        for (int purchase = 0; purchase < 20; purchase++) {
            result = BattlePassService.applyPurchase(player(result), Map.of("xp", 150), MONDAY + 7 * 86400 + 1);
        }
        assertEquals(3, result.getDailyPurchases());
        assertEquals(200, result.getDailyPurchasedXp());
        assertEquals(0, result.getExpbp());
    }
}
