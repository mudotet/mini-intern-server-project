package com.game.server;

import com.game.server.proto.BattlePassModel.*;
import com.game.server.proto.ShopModel.ShopDailySnapshotProto;
import com.game_server.general.dao.GameDao;
import com.game_server.general.service.*;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BattlePassServiceTest {
    @Test void viewDoesNotGrantRewardsAndOldPlayersStartAtZero() {
        GameDao dao = mock(GameDao.class);
        when(dao.player("p")).thenReturn(new GameDao.Player("a", "p", 0, Map.of("gem",100L,"gold",1000L), ShopDailySnapshotProto.getDefaultInstance()));
        when(dao.battlePass("p")).thenReturn(new GameDao.BattlePassState(-1, PlayerBattlePassStateProto.getDefaultInstance()));
        var result = new BattlePassService(dao, BattlePassCatalog.load(), Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"), ZoneOffset.UTC)).view("p");
        assertEquals(10, result.getNodesCount());
        assertEquals(0, result.getProgress().getTotalExpBp());
        assertFalse(result.getNodes(0).getReached());
        assertEquals(0, result.getOwnedHeroesCount());
        assertEquals(4, result.getHeroesCount());
        assertEquals(2, result.getResourcesCount());
    }
    @Test void concurrentClaimReturnsCommittedReceiptInsteadOfAlreadyClaimed() {
        GameDao dao = mock(GameDao.class);
        var catalog = BattlePassCatalog.load();
        var clock = Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"), ZoneOffset.UTC);
        String pass = catalog.active(clock).id;
        var request = com.game.server.proto.BattlePassClaimContract.BattlePassClaimRequestProto.newBuilder()
                .setBattlePassId(pass).setNodeId("node_1").build();
        var response = com.game.server.proto.BattlePassClaimContract.BattlePassClaimResponseProto.newBuilder()
                .setNodeId("node_1").setReward(catalog.node("node_1").getReward()).build();
        var receipt = new GameDao.BattlePassReceipt("claim",request.toByteArray(),response.toByteArray());
        when(dao.battlePassReceipt("p","request_1234567890")).thenReturn(null,receipt);
        when(dao.player("p")).thenReturn(new GameDao.Player("a","p",1,Map.of("gold",1100L),ShopDailySnapshotProto.getDefaultInstance()));
        when(dao.battlePass("p")).thenReturn(new GameDao.BattlePassState(1,PlayerBattlePassStateProto.newBuilder()
                .setProgress(BattlePassProgressProto.newBuilder().setBattlePassId(pass).setTotalExpBp(500).addClaimedNodeIds("node_1")).build()));
        assertEquals(response,new BattlePassService(dao,catalog,clock).claim("p",request,"request_1234567890"));
    }
    @Test void resourceOverflowRejectsClaimWithoutMarkingItClaimed() {
        GameDao dao=mock(GameDao.class);
        var catalog=BattlePassCatalog.load();
        var clock=Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"),ZoneOffset.UTC);
        String pass=catalog.active(clock).id;
        var progress=BattlePassProgressProto.newBuilder().setBattlePassId(pass).setTotalExpBp(500).build();
        var state=PlayerBattlePassStateProto.newBuilder().setProgress(progress).build();
        when(dao.player("p")).thenReturn(new GameDao.Player("a","p",0,Map.of("gold",Long.MAX_VALUE),ShopDailySnapshotProto.getDefaultInstance()));
        when(dao.battlePass("p")).thenReturn(new GameDao.BattlePassState(0,state));
        var request=com.game.server.proto.BattlePassClaimContract.BattlePassClaimRequestProto.newBuilder().setBattlePassId(pass).setNodeId("node_1").build();
        GameException error=assertThrows(GameException.class,()->new BattlePassService(dao,catalog,clock).claim("p",request,"request_1234567890"));
        assertEquals("RESOURCE_CAPACITY",error.code);
        assertEquals(0,state.getProgress().getClaimedNodeIdsCount());
    }
    @Test void rolloverBetweenReadingStateAndCommittingRejectsEveryMutation() {
        var catalog=BattlePassCatalog.load();
        Instant before=Instant.parse("2026-10-30T16:59:59Z"), end=before.plusSeconds(1);
        String pass=catalog.active(Clock.fixed(before,ZoneOffset.UTC)).id;
        for(int operation=0;operation<3;operation++) {
            GameDao dao=mock(GameDao.class);
            Clock clock=mock(Clock.class);
            when(clock.instant()).thenReturn(before,end);
            when(dao.player("p")).thenReturn(new GameDao.Player("a","p",0,Map.of("gold",1000L,"gem",100L),ShopDailySnapshotProto.getDefaultInstance()));
            when(dao.battlePass("p")).thenReturn(new GameDao.BattlePassState(0,PlayerBattlePassStateProto.newBuilder()
                    .setProgress(BattlePassProgressProto.newBuilder().setBattlePassId(pass).setTotalExpBp(500)).build()));
            BattlePassService service=new BattlePassService(dao,catalog,clock);
            Runnable mutation;
            if(operation==0) mutation=()->service.purchasePremium("p",com.game.server.proto.BattlePassPurchaseContract.BattlePassPurchaseRequestProto.newBuilder().setBattlePassId(pass).build(),"request_1234567890");
            else if(operation==1) mutation=()->service.claim("p",com.game.server.proto.BattlePassClaimContract.BattlePassClaimRequestProto.newBuilder().setBattlePassId(pass).setNodeId("node_1").build(),"request_1234567890");
            else mutation=()->service.grantQuestExp("p",com.game.server.proto.BattlePassQuestGrantContract.BattlePassQuestGrantRequestProto.newBuilder().setBattlePassId(pass).setQuestId("q").setGrantId("g").setExpBp(100).build(),"request_1234567890");
            assertEquals("BATTLE_PASS_EXPIRED",assertThrows(GameException.class,mutation::run).code);
        }
    }
    @Test void premiumPriceRejectsOneGemBelowTheRequiredAmount() {
        var catalog=BattlePassCatalog.load();
        var clock=Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"),ZoneOffset.UTC);
        String pass=catalog.active(clock).id;
        GameDao dao=mock(GameDao.class);
        when(dao.player("p")).thenReturn(new GameDao.Player("a","p",0,Map.of("gem",99L),ShopDailySnapshotProto.getDefaultInstance()));
        when(dao.battlePass("p")).thenReturn(new GameDao.BattlePassState(-1,PlayerBattlePassStateProto.getDefaultInstance()));
        var request=com.game.server.proto.BattlePassPurchaseContract.BattlePassPurchaseRequestProto.newBuilder().setBattlePassId(pass).build();
        assertEquals("INSUFFICIENT_FUNDS",assertThrows(GameException.class,()->new BattlePassService(dao,catalog,clock)
                .purchasePremium("p",request,"request_1234567890")).code);
    }
}
