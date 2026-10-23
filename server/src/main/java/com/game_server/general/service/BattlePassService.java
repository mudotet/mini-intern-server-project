package com.game_server.general.service;

import com.game.server.proto.BattlePassContract.*;
import com.game.server.proto.BattlePassBlueprintContract.BattlePassNodeConfig;
import com.game.server.proto.BattlePassModel.*;
import com.game.server.proto.BattlePassQuestGrantContract.*;
import com.game.server.proto.BattlePassPurchaseContract.*;
import com.game.server.proto.BattlePassClaimContract.*;
import com.game.server.proto.ResourceModel.Resource;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import com.game_server.general.dao.GameDao;
import com.game_server.general.dao.GameDao.BattlePassState;
import com.game_server.general.dao.GameDao.BattlePassReceipt;
import java.time.Clock;
import java.util.*;

public final class BattlePassService {
    private final GameDao dao;
    private final BattlePassCatalog catalog;
    private final Clock clock;
    public BattlePassService(GameDao dao, BattlePassCatalog catalog, Clock clock) {
        this.dao = dao; this.catalog = catalog; this.clock = clock;
    }

    public BattlePassResponseProto view(String playerId) {
        BattlePassCatalog.Pass pass = catalog.active(clock);
        GameDao.Player player = dao.player(playerId);
        BattlePassState state = current(dao.battlePass(playerId), pass.id);
        BattlePassProgressProto progress = progress(state.data);
        BattlePassResponseProto.Builder response = BattlePassResponseProto.newBuilder().setBattlePassId(pass.id)
                .setStartsAt(pass.start).setEndsAt(pass.end).setPremiumPriceGems(catalog.blueprint().getPremiumPriceGems())
                .setProgress(progress).addAllOwnedHeroes(heroes(state.data)).addAllResources(PlayerService.resourceList(player.resources));
        catalog.blueprint().getHeroesList().forEach(hero -> response.addHeroes(
                HeroProto.newBuilder().setHeroId(hero.getHeroId()).setRarity(hero.getRarity())));
        int position = 0;
        for (BattlePassNodeConfig node : catalog.blueprint().getNodesList()) {
            position++;
            boolean reached = progress.getTotalExpBp() >= position * 500L;
            boolean claimed = progress.getClaimedNodeIdsList().contains(node.getNodeId());
            response.addNodes(BattlePassNodeProto.newBuilder().setNodeId(node.getNodeId()).setPosition(position)
                    .setRequiredTotalExp(position * 500L).setPremium(node.getPremium()).setReward(node.getReward())
                    .setReached(reached).setClaimed(claimed).setClaimable(reached && !claimed && (!node.getPremium() || progress.getPremiumOwned())));
        }
        return response.build();
    }

    public BattlePassQuestGrantResponseProto grantQuestExp(String playerId, BattlePassQuestGrantRequestProto request, String requestId) {
        GameException.requestId(requestId);
        if (!BattlePassCatalog.id(request.getQuestId()) || !BattlePassCatalog.id(request.getGrantId()) || request.getExpBp() <= 0)
            throw new GameException(400, "INVALID_QUEST_GRANT", "Use Quest/grant IDs and positive EXP");
        byte[] input = BattlePassQuestGrantRequestProto.newBuilder().setBattlePassId(request.getBattlePassId())
                .setQuestId(request.getQuestId()).setGrantId(request.getGrantId()).setExpBp(request.getExpBp()).build().toByteArray();
        for (int attempt = 0; attempt < 20; attempt++) {
            BattlePassReceipt receipt = matching(dao.battlePassReceipt(playerId, requestId), "quest", input);
            if (receipt != null) return decode(receipt, BattlePassQuestGrantResponseProto.parser());
            receipt = matching(dao.questGrant(playerId, request.getGrantId()), "quest", input);
            if (receipt != null) {
                if (dao.linkBattlePassReceipt(playerId, requestId, receipt)) return decode(receipt, BattlePassQuestGrantResponseProto.parser());
                continue;
            }
            BattlePassCatalog.Pass pass = active(request.getBattlePassId());
            GameDao.Player player = dao.player(playerId);
            BattlePassState state = current(dao.battlePass(playerId), pass.id);
            long exp = state.data.getProgress().getTotalExpBp();
            long awarded = Math.min(request.getExpBp(), catalog.maxExp() - exp);
            PlayerBattlePassStateProto next = state.data.toBuilder().setProgress(state.data.getProgress().toBuilder()
                    .setTotalExpBp(exp + awarded)).build();
            BattlePassQuestGrantResponseProto response = BattlePassQuestGrantResponseProto.newBuilder()
                    .setProgress(progress(next)).setExpBpGranted(awarded).build();
            active(pass.id);
            if (dao.commitBattlePass(player, new BattlePassState(state.version, next), requestId,
                    new BattlePassReceipt("quest", input, response.toByteArray()), request.getGrantId())) return response;
        }
        throw retry();
    }

    public BattlePassPurchaseResponseProto purchasePremium(String playerId, BattlePassPurchaseRequestProto request, String requestId) {
        GameException.requestId(requestId);
        byte[] input = BattlePassPurchaseRequestProto.newBuilder().setBattlePassId(request.getBattlePassId()).build().toByteArray();
        for (int attempt = 0; attempt < 20; attempt++) {
            BattlePassReceipt receipt = matching(dao.battlePassReceipt(playerId, requestId), "purchase", input);
            if (receipt != null) return decode(receipt, BattlePassPurchaseResponseProto.parser());
            BattlePassCatalog.Pass pass = active(request.getBattlePassId());
            GameDao.Player player = dao.player(playerId);
            BattlePassState state = current(dao.battlePass(playerId), pass.id);
            Map<String, Long> resources = new TreeMap<>(player.resources);
            if (!state.data.getProgress().getPremiumOwned()) {
                long gems = resources.getOrDefault("gem", 0L);
                long price = catalog.blueprint().getPremiumPriceGems();
                if (gems < price) throw new GameException(409, "INSUFFICIENT_FUNDS", "Not enough Gems for Premium");
                resources.put("gem", gems - price);
            }
            PlayerBattlePassStateProto next = state.data.toBuilder().setProgress(state.data.getProgress().toBuilder()
                    .setPremiumOwned(true)).build();
            BattlePassPurchaseResponseProto response = BattlePassPurchaseResponseProto.newBuilder()
                    .setProgress(progress(next)).addAllResources(PlayerService.resourceList(resources)).build();
            active(pass.id);
            if (dao.commitBattlePass(new GameDao.Player(player.accountId, playerId, player.version, resources, player.daily),
                    new BattlePassState(state.version, next), requestId, new BattlePassReceipt("purchase", input, response.toByteArray()), null)) return response;
        }
        throw retry();
    }

    public BattlePassClaimResponseProto claim(String playerId, BattlePassClaimRequestProto request, String requestId) {
        GameException.requestId(requestId);
        byte[] input = BattlePassClaimRequestProto.newBuilder().setBattlePassId(request.getBattlePassId())
                .setNodeId(request.getNodeId()).build().toByteArray();
        for (int attempt = 0; attempt < 20; attempt++) {
            BattlePassReceipt receipt = matching(dao.battlePassReceipt(playerId, requestId), "claim", input);
            if (receipt != null) return decode(receipt, BattlePassClaimResponseProto.parser());
            BattlePassCatalog.Pass pass = active(request.getBattlePassId());
            BattlePassNodeConfig node = catalog.node(request.getNodeId());
            if (node == null) throw new GameException(404, "NODE_NOT_FOUND", "Battle Pass node not found");
            GameDao.Player player = dao.player(playerId);
            BattlePassState state = current(dao.battlePass(playerId), pass.id);
            // Another request can commit between the first receipt lookup and these state reads.
            receipt = matching(dao.battlePassReceipt(playerId, requestId), "claim", input);
            if (receipt != null) return decode(receipt, BattlePassClaimResponseProto.parser());
            int position = catalog.blueprint().getNodesList().indexOf(node) + 1;
            if (state.data.getProgress().getTotalExpBp() < position * 500L)
                throw new GameException(409, "NODE_NOT_REACHED", "Reach the full node EXP requirement first");
            if (node.getPremium() && !state.data.getProgress().getPremiumOwned())
                throw new GameException(409, "PREMIUM_REQUIRED", "This node requires Premium access");
            if (state.data.getProgress().getClaimedNodeIdsList().contains(node.getNodeId()))
                throw new GameException(409, "NODE_ALREADY_CLAIMED", "Node reward was already claimed");
            Map<String, Long> resources = new TreeMap<>(player.resources);
            PlayerBattlePassStateProto.Builder next = state.data.toBuilder();
            BattlePassRewardProto reward = node.getReward();
            if (reward.hasHeroId()) {
                String heroId = reward.getHeroId();
                if (state.data.getOwnedHeroIdsList().contains(heroId)) {
                    reward = BattlePassRewardProto.newBuilder().setResource(Resource.newBuilder().setResourceId("gem")
                            .setQuantity(catalog.compensation(catalog.hero(heroId).getRarity()))).build();
                } else next.addOwnedHeroIds(heroId);
            }
            if (reward.hasResource()) {
                Resource resource = reward.getResource();
                try { resources.put(resource.getResourceId(), Math.addExact(resources.getOrDefault(resource.getResourceId(), 0L), resource.getQuantity())); }
                catch (ArithmeticException overflow) { throw new GameException(409, "RESOURCE_CAPACITY", "Resource capacity reached"); }
            }
            next.setProgress(state.data.getProgress().toBuilder().addClaimedNodeIds(node.getNodeId()));
            PlayerBattlePassStateProto data = next.build();
            BattlePassClaimResponseProto response = BattlePassClaimResponseProto.newBuilder().setNodeId(node.getNodeId())
                    .setReward(reward).setProgress(progress(data)).addAllResources(PlayerService.resourceList(resources))
                    .addAllOwnedHeroes(heroes(data)).build();
            active(pass.id);
            if (dao.commitBattlePass(new GameDao.Player(player.accountId, playerId, player.version, resources, player.daily),
                    new BattlePassState(state.version, data), requestId, new BattlePassReceipt("claim", input, response.toByteArray()), null)) return response;
        }
        throw retry();
    }

    private BattlePassCatalog.Pass active(String requestedId) {
        if (!BattlePassCatalog.id(requestedId)) throw new GameException(400, "INVALID_BATTLE_PASS", "Specify a Battle Pass ID");
        BattlePassCatalog.Pass pass = catalog.active(clock);
        if (!pass.id.equals(requestedId)) throw new GameException(409, "BATTLE_PASS_EXPIRED", "Request does not target the active Battle Pass");
        return pass;
    }
    private static BattlePassReceipt matching(BattlePassReceipt receipt, String operation, byte[] input) {
        if (receipt != null && (!receipt.operation.equals(operation) || !Arrays.equals(receipt.input, input)))
            throw new GameException(409, "REQUEST_ID_REUSED", "Request or grant ID belongs to different input");
        return receipt;
    }
    private static <T extends Message> T decode(BattlePassReceipt receipt, Parser<T> parser) {
        try { return parser.parseFrom(receipt.response); }
        catch (InvalidProtocolBufferException malformed) { throw new IllegalStateException("Invalid Battle Pass receipt", malformed); }
    }
    private static GameException retry() { return new GameException(409, "RETRY", "Retry this request"); }

    private BattlePassState current(BattlePassState state, String passId) {
        if (state.data.getProgress().getBattlePassId().equals(passId)) return state;
        return new BattlePassState(state.version, state.data.toBuilder()
                .setProgress(BattlePassProgressProto.newBuilder().setBattlePassId(passId)).build());
    }
    private BattlePassProgressProto progress(PlayerBattlePassStateProto state) {
        long exp = state.getProgress().getTotalExpBp();
        return state.getProgress().toBuilder().setReachedNodes((int) (exp / 500))
                .setExpIntoNextNode(exp % 500).setCompleted(exp >= catalog.maxExp()).build();
    }
    private List<HeroProto> heroes(PlayerBattlePassStateProto state) {
        List<HeroProto> result = new ArrayList<>();
        for (String id : new TreeSet<>(state.getOwnedHeroIdsList())) {
            var hero = catalog.hero(id);
            if (hero == null) throw new IllegalStateException("Owned Hero missing from catalog: " + id);
            result.add(HeroProto.newBuilder().setHeroId(id).setRarity(hero.getRarity()).build());
        }
        return result;
    }
}
