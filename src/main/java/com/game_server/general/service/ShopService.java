package com.game_server.general.service;

import com.game.server.proto.ShopBlueprintContract.*;
import com.game.server.proto.ShopDailyResponse.*;
import com.game.server.proto.ShopStaticResponse.*;
import com.game.server.proto.ShopModel.*;
import com.game.server.proto.ShopPackagesContract.ShopPackagesResponseProto;
import com.game_server.general.dao.GameDao;
import com.game_server.general.dao.GameDao.Player;
import java.time.Clock;
import java.util.*;

public final class ShopService {
    private final GameDao dao;
    private final ShopCatalog catalog;
    private final Clock clock;
    private final Random random;

    public ShopService(GameDao dao, ShopCatalog catalog, Clock clock, Random random) {
        this.dao = dao; this.catalog = catalog; this.clock = clock; this.random = random;
    }
    public ShopPackagesResponseProto packages() {
        return ShopPackagesResponseProto.newBuilder().addAllPackages(catalog.packages()).build();
    }
    public ShopStaticProtoResponse staticShop() {
        ShopStaticProtoResponse.Builder response = ShopStaticProtoResponse.newBuilder().setServerTime(now());
        for (StaticShopSlotConfig slot : catalog.blueprint.getStaticShop().getSlotsList()) {
            ShopPackage product = catalog.packageById(slot.getPackageId());
            response.addSlots(ShopStaticSlotResponse.newBuilder().setSlotId(slot.getSlotId()).setOffer(StaticOfferProto.newBuilder()
                    .setOfferId("static:" + product.getId()).setPackageId(product.getId()).setPrice(product.getPrice()).putAllItems(product.getItemsMap())));
        }
        return response.build();
    }
    public ShopDailyProtoResponse dailyShop(String playerId) {
        Player player = dailyPlayer(playerId);
        ShopDailyProtoResponse.Builder response = ShopDailyProtoResponse.newBuilder().setCycleStart(player.daily.getCycleStart())
                .setExpiresAt(player.daily.getExpiresAt()).setServerTime(now());
        for (ShopDailySlotSnapshotProto slot : player.daily.getSlotsList()) {
            ShopDailyOfferSnapshotProto offer = slot.getOffer();
            response.addSlots(ShopDailySlotResponse.newBuilder().setSlotId(slot.getSlotId()).setOffer(DailyOfferProto.newBuilder()
                    .setOfferId(offer.getOfferId()).setPackageId(offer.getPackageId()).setOriginalPrice(offer.getOriginalPrice())
                    .setFinalPrice(offer.getFinalPrice()).putAllItems(offer.getItemsMap()).setDiscountPercent(offer.getDiscountPercent())
                    .setPurchased(offer.getPurchased()).setRemaining(offer.getRemaining()).setPurchaseLimit(offer.getPurchaseLimit())));
        }
        return response.build();
    }
    public Player dailyPlayer(String playerId) {
        for (int attempt = 0; attempt < 20; attempt++) {
            Player player = dao.player(playerId);
            long time = now();
            if (player.daily.getSlotsCount() > 0 && player.daily.getCycleStart() <= time && time < player.daily.getExpiresAt()) return player;
            Player updated = new Player(player.accountId, playerId, player.version, player.resources, snapshot(time));
            if (dao.save(updated)) return new Player(updated.accountId, playerId, updated.version + 1, updated.resources, updated.daily);
        }
        throw new GameException(409, "RETRY", "Retry this request");
    }
    ShopPackage product(String id) { return catalog.packageById(id); }
    long now() { return clock.instant().getEpochSecond(); }

    private synchronized ShopDailySnapshotProto snapshot(long time) {
        DailyShopConfig config = catalog.blueprint.getDaily();
        long start = config.getCycleAnchor() + Math.floorDiv(time - config.getCycleAnchor(), config.getCycleDurationSeconds()) * config.getCycleDurationSeconds();
        ShopDailySnapshotProto.Builder snapshot = ShopDailySnapshotProto.newBuilder().setCycleStart(start).setExpiresAt(start + config.getCycleDurationSeconds());
        List<String> products = new ArrayList<>();
        config.getCandidatesList().forEach(candidate -> products.add(candidate.getPackageId()));
        Collections.shuffle(products, random);
        for (int index = 0; index < config.getSlotsCount(); index++) {
            ShopPackage product = catalog.packageById(products.get(index));
            String slotId = config.getSlots(index).getSlotId();
            int discount = config.getDiscountPercentages(random.nextInt(config.getDiscountPercentagesCount()));
            ShopPackage.Price.Builder price = ShopPackage.Price.newBuilder();
            product.getPrice().getCurrencyMap().forEach((id, amount) -> price.putCurrency(id, (int) Math.max(1, (long) amount * (100 - discount) / 100)));
            ShopDailyOfferSnapshotProto.Builder offer = ShopDailyOfferSnapshotProto.newBuilder()
                    .setOfferId("daily:" + start + ":" + slotId + ":" + product.getId()).setPackageId(product.getId())
                    .setOriginalPrice(product.getPrice()).setFinalPrice(price).setDiscountPercent(discount)
                    .setPurchaseLimit(config.getPurchaseLimit()).setRemaining(config.getPurchaseLimit());
            new TreeMap<>(product.getDailyRewardRangesMap()).forEach((id, range) -> offer.putItems(id,
                    range.getMin() + random.nextInt(range.getMax() - range.getMin() + 1)));
            snapshot.addSlots(ShopDailySlotSnapshotProto.newBuilder().setSlotId(slotId).setOffer(offer));
        }
        return snapshot.build();
    }
}
