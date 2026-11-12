package com.game_server.general.service;

import com.game.server.proto.BattlePassContract.*;
import com.game.server.proto.ShopBlueprintContract.ShopPackage;
import com.game_server.general.dao.GameDao;
import com.game_server.general.dao.GameDao.BattlePassReceipt;
import com.game_server.general.dao.GameDao.Player;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class BattlePassService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long DAY = 86400;
    private static final long WEEK = 7 * DAY;
    private final GameDao dao;
    private final Clock clock;

    public BattlePassService(GameDao dao, Clock clock) {
        this.dao = Objects.requireNonNull(dao);
        this.clock = Objects.requireNonNull(clock);
    }

    public BattlePassStateProto status(String playerId) {
        for (int attempt = 0; attempt < 20; attempt++) {
            Player player = dao.player(playerId);
            long now = clock.instant().getEpochSecond();
            BattlePassSnapshotProto snapshot = normalize(player.battlePass, now);
            if (snapshot.equals(player.battlePass)) return view(snapshot, now);
            if (dao.save(new Player(player.accountId, player.playerId, player.version, player.resources, player.daily, snapshot)))
                return view(snapshot, now);
        }
        throw retry();
    }

    public BattlePassMutationResponseProto claim(String playerId, BattlePassClaimRequestProto request, String requestId) {
        return mutate(playerId, "claim", request.getPassId(), request.getNodeId(), requestId);
    }

    public BattlePassMutationResponseProto purchasePremium(String playerId, BattlePassPremiumRequestProto request, String requestId) {
        return mutate(playerId, "premium", request.getPassId(), "", requestId);
    }

    public BattlePassMutationResponseProto claimQuest(String playerId, BattlePassQuestClaimRequestProto request, String requestId) {
        return mutate(playerId, "quest", request.getPassId(), request.getQuestId(), requestId);
    }

    /** Progress is committed inside the same Player/receipt transaction as the shop exchange. */
    public static BattlePassSnapshotProto applyPurchase(Player player, Map<String, Integer> rewards, long now) {
        BattlePassSnapshotProto snapshot = normalize(player.battlePass, now);
        return snapshot.toBuilder().setDailyPurchases(Math.min(3, snapshot.getDailyPurchases() + 1))
                .setDailyPurchasedXp((int) Math.min(200L, (long) snapshot.getDailyPurchasedXp() + rewards.getOrDefault("xp", 0))).build();
    }

    private BattlePassMutationResponseProto mutate(String playerId, String kind, String passId, String target, String requestId) {
        GameException.requestId(requestId);
        String fingerprint = kind + ":" + passId.length() + ":" + passId + ":" + target.length() + ":" + target;
        for (int attempt = 0; attempt < 20; attempt++) {
            BattlePassReceipt receipt = dao.battlePassReceipt(playerId, requestId);
            if (receipt != null) return replay(receipt, fingerprint);
            if (passId.isEmpty() || passId.length() > 128 || target.length() > 128)
                throw new GameException(400, "INVALID_REQUEST", "Supply the current pass and a valid target");
            Player player = dao.player(playerId);
            receipt = dao.battlePassReceipt(playerId, requestId);
            if (receipt != null) return replay(receipt, fingerprint);
            long now = clock.instant().getEpochSecond();
            BattlePassSnapshotProto snapshot = normalize(player.battlePass, now);
            if (!passId(snapshot).equals(passId)) throw new GameException(409, "PASS_EXPIRED", "This battle pass season has ended");
            Map<String, Long> resources = new TreeMap<>(player.resources);
            Map<String, Integer> rewards = Map.of();
            BattlePassSnapshotProto.Builder updated = snapshot.toBuilder();
            switch (kind) {
                case "premium":
                    if (snapshot.getPremiumOwned()) throw new GameException(409, "ALREADY_PREMIUM", "Premium is already unlocked for this season");
                    long gems = resources.getOrDefault("gem", 0L);
                    if (gems < BattlePassCatalog.PREMIUM_GEMS) throw new GameException(409, "INSUFFICIENT_FUNDS", "Not enough Gems to unlock Premium");
                    resources.put("gem", gems - BattlePassCatalog.PREMIUM_GEMS);
                    updated.setPremiumOwned(true);
                    break;
                case "claim":
                    BattlePassCatalog.Milestone milestone = milestone(target);
                    boolean premium = target.startsWith("premium_");
                    if (snapshot.getClaimedNodesList().contains(target)) throw new GameException(409, "ALREADY_CLAIMED", "This reward has already been claimed");
                    if (snapshot.getExpbp() < milestone.requiredExpbp) throw new GameException(409, "INSUFFICIENT_EXPBP", "Earn more EXPBP to reach this reward");
                    if (premium && !snapshot.getPremiumOwned()) throw new GameException(409, "PREMIUM_REQUIRED", "Unlock Premium to claim this reward");
                    rewards = premium ? milestone.premium : milestone.free;
                    credit(resources, rewards);
                    updated.addClaimedNodes(target);
                    break;
                case "quest":
                    BattlePassCatalog.Quest quest = quest(target, snapshot.getQuestDayStart());
                    if (snapshot.getClaimedQuestsList().contains(quest.id)) throw new GameException(409, "ALREADY_CLAIMED", "This quest reward has already been claimed today");
                    if (progress(snapshot, quest) < quest.target) throw new GameException(409, "QUEST_INCOMPLETE", "Complete this quest before claiming its EXPBP");
                    updated.addClaimedQuests(quest.id).setExpbp(Math.addExact(snapshot.getExpbp(), quest.expbpReward));
                    rewards = Map.of("expbp", quest.expbpReward);
                    break;
                default:
                    throw new IllegalStateException("Unsupported battle pass action");
            }
            BattlePassSnapshotProto result = updated.build();
            BattlePassMutationResponseProto response = BattlePassMutationResponseProto.newBuilder().setPass(view(result, now))
                    .addAllItems(PlayerService.resourceList(rewards)).addAllResources(PlayerService.resourceList(resources)).build();
            Player changed = new Player(player.accountId, player.playerId, player.version, resources, player.daily, result);
            if (dao.battlePassMutation(changed, requestId, fingerprint, response)) return response;
        }
        BattlePassReceipt receipt = dao.battlePassReceipt(playerId, requestId);
        if (receipt != null) return replay(receipt, fingerprint);
        throw retry();
    }

    private static BattlePassMutationResponseProto replay(BattlePassReceipt receipt, String fingerprint) {
        if (!receipt.fingerprint.equals(fingerprint)) throw new GameException(409, "REQUEST_ID_REUSED", "Request ID belongs to different input");
        return receipt.response;
    }

    private static BattlePassSnapshotProto normalize(BattlePassSnapshotProto snapshot, long now) {
        LocalDate date = Instant.ofEpochSecond(now).atZone(ZONE).toLocalDate();
        long day = date.atStartOfDay(ZONE).toEpochSecond();
        long start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(ZONE).toEpochSecond();
        if (snapshot.getSeasonStart() != start) {
            return BattlePassSnapshotProto.newBuilder().setSeasonStart(start).setQuestDayStart(day).build();
        }
        if (snapshot.getQuestDayStart() != day) {
            return snapshot.toBuilder().setQuestDayStart(day).setDailyPurchases(0).setDailyPurchasedXp(0).clearClaimedQuests().build();
        }
        return snapshot;
    }

    private static BattlePassStateProto view(BattlePassSnapshotProto snapshot, long now) {
        BattlePassStateProto.Builder state = BattlePassStateProto.newBuilder().setPassId(passId(snapshot)).setName(BattlePassCatalog.NAME)
                .setStartsAt(snapshot.getSeasonStart()).setEndsAt(snapshot.getSeasonStart() + WEEK).setServerTime(now)
                .setExpbp(snapshot.getExpbp()).setPremiumOwned(snapshot.getPremiumOwned())
                .setPremiumPrice(ShopPackage.Price.newBuilder().putCurrency("gem", BattlePassCatalog.PREMIUM_GEMS))
                .setQuestsResetAt(snapshot.getQuestDayStart() + DAY);
        for (BattlePassCatalog.Milestone milestone : BattlePassCatalog.MILESTONES) {
            state.addMilestones(BattlePassMilestoneProto.newBuilder().setStep(milestone.step).setRequiredExpbp(milestone.requiredExpbp)
                    .setFree(node(snapshot, milestone, false)).setPremium(node(snapshot, milestone, true)));
        }
        for (BattlePassCatalog.Quest quest : BattlePassCatalog.QUESTS) {
            int progress = progress(snapshot, quest);
            boolean claimed = snapshot.getClaimedQuestsList().contains(quest.id);
            state.addQuests(BattlePassQuestProto.newBuilder().setQuestId(quest.id + ":" + snapshot.getQuestDayStart())
                    .setTitle(quest.title).setDescription(quest.description).setProgress(progress).setTarget(quest.target)
                    .setExpbpReward(quest.expbpReward).setClaimed(claimed).setClaimable(!claimed && progress >= quest.target));
        }
        return state.build();
    }

    private static BattlePassNodeProto node(BattlePassSnapshotProto snapshot, BattlePassCatalog.Milestone milestone, boolean premium) {
        String id = (premium ? "premium_" : "free_") + milestone.step;
        boolean claimed = snapshot.getClaimedNodesList().contains(id);
        return BattlePassNodeProto.newBuilder().setNodeId(id).putAllItems(premium ? milestone.premium : milestone.free)
                .setClaimed(claimed).setClaimable(!claimed && snapshot.getExpbp() >= milestone.requiredExpbp
                        && (!premium || snapshot.getPremiumOwned())).build();
    }

    private static BattlePassCatalog.Milestone milestone(String nodeId) {
        for (BattlePassCatalog.Milestone milestone : BattlePassCatalog.MILESTONES) {
            if (nodeId.equals("free_" + milestone.step) || nodeId.equals("premium_" + milestone.step)) return milestone;
        }
        throw new GameException(404, "NODE_NOT_FOUND", "Battle pass reward not found");
    }

    private static BattlePassCatalog.Quest quest(String questId, long day) {
        for (BattlePassCatalog.Quest quest : BattlePassCatalog.QUESTS) {
            if (questId.equals(quest.id + ":" + day)) return quest;
            if (questId.startsWith(quest.id + ":")) throw new GameException(409, "QUEST_EXPIRED", "This daily quest has expired");
        }
        throw new GameException(404, "QUEST_NOT_FOUND", "Battle pass quest not found");
    }

    private static int progress(BattlePassSnapshotProto snapshot, BattlePassCatalog.Quest quest) {
        return Math.min(quest.target, quest.id.equals("study") ? snapshot.getDailyPurchasedXp() : snapshot.getDailyPurchases());
    }

    private static String passId(BattlePassSnapshotProto snapshot) { return "weekly:" + snapshot.getSeasonStart(); }

    private static void credit(Map<String, Long> resources, Map<String, Integer> rewards) {
        for (Map.Entry<String, Integer> reward : rewards.entrySet()) {
            try { resources.put(reward.getKey(), Math.addExact(resources.getOrDefault(reward.getKey(), 0L), reward.getValue())); }
            catch (ArithmeticException overflow) { throw new GameException(409, "RESOURCE_CAPACITY", "Resource capacity reached"); }
        }
    }

    private static GameException retry() { return new GameException(409, "RETRY", "Retry this request"); }
}
