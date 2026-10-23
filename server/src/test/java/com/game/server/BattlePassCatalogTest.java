package com.game.server;

import com.game_server.general.service.BattlePassCatalog;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BattlePassCatalogTest {
    @Test void tenNodesHaveOneRewardAndStableSharedDates() {
        BattlePassCatalog catalog = BattlePassCatalog.load();
        assertEquals(10, catalog.blueprint().getNodesCount());
        assertEquals(100, catalog.blueprint().getPremiumPriceGems());
        assertEquals(5000, catalog.maxExp());
        assertEquals(100, catalog.node("node_1").getReward().getResource().getQuantity());
        assertEquals("hero_phoenix", catalog.node("node_10").getReward().getHeroId());
        var first = catalog.active(Clock.fixed(Instant.parse("2026-09-30T17:00:00Z"), ZoneOffset.UTC));
        var last = catalog.active(Clock.fixed(Instant.parse("2026-10-30T17:00:00Z").minusSeconds(1), ZoneOffset.UTC));
        var next = catalog.active(Clock.fixed(Instant.parse("2026-10-30T17:00:00Z"), ZoneOffset.UTC));
        assertEquals(first.id, last.id);
        assertNotEquals(first.id, next.id);
        assertEquals(first.end, next.start);
    }
    @Test void configurationRejectsMissingRewardsUnknownHeroesAndInvalidSchedules() {
        var base=BattlePassCatalog.load().blueprint();
        assertThrows(IllegalArgumentException.class,()->new BattlePassCatalog(base.toBuilder().setNodes(0,base.getNodes(0).toBuilder().clearReward()).build()));
        assertThrows(IllegalArgumentException.class,()->new BattlePassCatalog(base.toBuilder().setNodes(0,base.getNodes(0).toBuilder()
                .setReward(com.game.server.proto.BattlePassModel.BattlePassRewardProto.newBuilder().setHeroId("unknown"))).build()));
        assertThrows(IllegalArgumentException.class,()->new BattlePassCatalog(base.toBuilder().setDurationSeconds(0).build()));
        assertThrows(IllegalArgumentException.class,()->new BattlePassCatalog(base.toBuilder().setScheduleOrigin(Long.MAX_VALUE).build()));
        assertThrows(IllegalArgumentException.class,()->new BattlePassCatalog(base.toBuilder().addNodes(base.getNodes(0)).build()));
        assertThrows(IllegalArgumentException.class,()->new BattlePassCatalog(base.toBuilder().clearCompensation().build()));
        assertThrows(IllegalArgumentException.class,()->new BattlePassCatalog(base.toBuilder().setExpPerNode(499).build()));
        var invalid=base.getNodes(0).getReward().getResource().toBuilder().setQuantity(-1).build();
        assertThrows(IllegalArgumentException.class,()->new BattlePassCatalog(base.toBuilder().setNodes(0,base.getNodes(0).toBuilder()
                .setReward(com.game.server.proto.BattlePassModel.BattlePassRewardProto.newBuilder().setResource(invalid))).build()));
        assertThrows(com.game_server.general.service.GameException.class,()->BattlePassCatalog.load()
                .active(Clock.fixed(Instant.parse("2026-09-30T16:59:59Z"),ZoneOffset.UTC)));
    }

    @Test void nodeCountComesFromConfigurationAndRewardUsesOneof() {
        var base=BattlePassCatalog.load().blueprint();
        var smaller=new BattlePassCatalog(base.toBuilder().removeNodes(9).build());
        assertEquals(4500,smaller.maxExp());
        var reward=com.game.server.proto.BattlePassModel.BattlePassRewardProto.newBuilder().setHeroId("hero_scout")
                .setResource(base.getNodes(0).getReward().getResource()).build();
        assertTrue(reward.hasResource());
        assertFalse(reward.hasHeroId());
        assertEquals(1,reward.getAllFields().size());
    }
    @Test void templateIdsReserveSpaceForTheSeasonSuffix() {
        var base=BattlePassCatalog.load().blueprint();
        assertThrows(IllegalArgumentException.class,()->new BattlePassCatalog(base.toBuilder().setId("a".repeat(109)).build()));
        var catalog=new BattlePassCatalog(base.toBuilder().setId("a".repeat(108)).build());
        assertTrue(BattlePassCatalog.id(catalog.active(Clock.fixed(Instant.parse("2027-09-30T17:00:00Z"),ZoneOffset.UTC)).id));
    }
}
