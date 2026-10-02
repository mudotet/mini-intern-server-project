package com.game_server.general.service;

import com.game.server.proto.ShopBlueprintContract.ShopPackage;
import com.game.server.proto.ShopModel.*;
import com.game.server.proto.ShopPurchaseRequest.ShopPurchaseRequestProto;
import com.game.server.proto.ShopPurchaseResponse.ShopPurchaseResponseProto;
import com.game_server.general.dao.GameDao;
import com.game_server.general.dao.GameDao.Player;
import com.game_server.general.dao.GameDao.Receipt;
import java.util.*;

public final class PurchaseService {
    private final GameDao dao;
    private final ShopService shop;
    public PurchaseService(GameDao dao, ShopService shop) { this.dao = dao; this.shop = shop; }

    public ShopPurchaseResponseProto purchase(String playerId, ShopPurchaseRequestProto request, String requestId) {
        GameException.requestId(requestId);
        String offerId = request.getId();
        int amount = request.getAmount();
        for (int attempt = 0; attempt < 20; attempt++) {
            Receipt receipt = dao.receipt(playerId, requestId);
            if (receipt != null) return replay(receipt, offerId, amount);
            if (amount != 1 || offerId.isEmpty() || offerId.length() > 256)
                throw new GameException(400, "INVALID_PURCHASE", "Select one offer and amount one");
            boolean daily = offerId.startsWith("daily:");
            Player player = daily ? shop.dailyPlayer(playerId) : dao.player(playerId);
            receipt = dao.receipt(playerId, requestId);
            if (receipt != null) return replay(receipt, offerId, amount);
            Map<String, Integer> price;
            Map<String, Integer> rewards;
            ShopDailySnapshotProto snapshot = player.daily;
            if (daily) {
                long now = shop.now();
                if (now < snapshot.getCycleStart() || now >= snapshot.getExpiresAt()) continue;
                int index = -1;
                for (int slot = 0; slot < snapshot.getSlotsCount(); slot++)
                    if (snapshot.getSlots(slot).getOffer().getOfferId().equals(offerId)) index = slot;
                if (index < 0) {
                    String[] parts = offerId.split(":", -1);
                    if (parts.length == 4) {
                        try {
                            if (Long.parseLong(parts[1]) != snapshot.getCycleStart())
                                throw new GameException(409, "OFFER_EXPIRED", "Daily offer has expired");
                        } catch (NumberFormatException malformed) { }
                    }
                    throw new GameException(404, "OFFER_NOT_FOUND", "Offer not found");
                }
                ShopDailySlotSnapshotProto slot = snapshot.getSlots(index);
                ShopDailyOfferSnapshotProto offer = slot.getOffer();
                if (offer.getPurchased() >= offer.getPurchaseLimit())
                    throw new GameException(409, "PURCHASE_LIMIT", "Daily purchase limit reached");
                price = offer.getFinalPrice().getCurrencyMap(); rewards = offer.getItemsMap();
                snapshot = snapshot.toBuilder().setSlots(index, slot.toBuilder().setOffer(offer.toBuilder()
                        .setPurchased(offer.getPurchased() + 1).setRemaining(offer.getPurchaseLimit() - offer.getPurchased() - 1))).build();
            } else {
                ShopPackage product = offerId.startsWith("static:") ? shop.product(offerId.substring(7)) : null;
                if (product == null) throw new GameException(404, "OFFER_NOT_FOUND", "Offer not found");
                price = product.getPrice().getCurrencyMap(); rewards = product.getItemsMap();
            }
            Map<String, Long> resources = new TreeMap<>(player.resources);
            for (Map.Entry<String, Integer> cost : price.entrySet()) {
                long balance = resources.getOrDefault(cost.getKey(), 0L);
                if (balance < cost.getValue()) throw new GameException(409, "INSUFFICIENT_FUNDS", "Not enough resources");
                resources.put(cost.getKey(), balance - cost.getValue());
            }
            for (Map.Entry<String, Integer> reward : rewards.entrySet()) {
                try { resources.put(reward.getKey(), Math.addExact(resources.getOrDefault(reward.getKey(), 0L), reward.getValue())); }
                catch (ArithmeticException overflow) { throw new GameException(409, "INVALID_PURCHASE", "Resource capacity reached"); }
            }
            ShopPurchaseResponseProto response = ShopPurchaseResponseProto.newBuilder().setOfferId(offerId)
                    .addAllItems(PlayerService.resourceList(rewards)).addAllResources(PlayerService.resourceList(resources)).build();
            if (dao.purchase(new Player(player.accountId, playerId, player.version, resources, snapshot), requestId, offerId, amount, response)) return response;
        }
        Receipt receipt = dao.receipt(playerId, requestId);
        if (receipt != null) return replay(receipt, offerId, amount);
        throw new GameException(409, "RETRY", "Retry this request");
    }
    private static ShopPurchaseResponseProto replay(Receipt receipt, String offerId, int amount) {
        if (!receipt.offerId.equals(offerId) || receipt.amount != amount)
            throw new GameException(409, "REQUEST_ID_REUSED", "Request ID belongs to different input");
        return receipt.response;
    }
}
