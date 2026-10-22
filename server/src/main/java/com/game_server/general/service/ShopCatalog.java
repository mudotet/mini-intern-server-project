package com.game_server.general.service;

import com.game.server.proto.ShopBlueprintContract.*;
import com.google.protobuf.TextFormat;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ShopCatalog {
    public final ShopBlueprint blueprint;
    private final Map<String, ShopPackage> packages;

    public ShopCatalog(ShopBlueprint blueprint) {
        this.blueprint = Objects.requireNonNull(blueprint);
        Map<String, ShopPackage> values = new LinkedHashMap<>();
        for (ShopPackage product : blueprint.getPackagesList()) {
            require(product.getId().matches("[A-Za-z0-9_-]{1,64}") && values.put(product.getId(), product) == null, "Invalid package ID");
            require(product.getPrice().getCurrencyCount() == 1, "Package must have one price");
            product.getPrice().getCurrencyMap().forEach((id, amount) -> require((id.equals("gold") || id.equals("gem")) && amount > 0, "Invalid package price"));
            require(!product.getItemsMap().isEmpty() && product.getItemsMap().keySet().equals(product.getDailyRewardRangesMap().keySet()), "Rewards must match daily ranges");
            product.getItemsMap().forEach((id, amount) -> {
                require(Set.of("gold", "gem", "xp").contains(id) && amount > 0, "Invalid reward");
                RewardQuantityRange range = product.getDailyRewardRangesOrThrow(id);
                require(range.getMin() > 0 && range.getMax() >= range.getMin(), "Invalid reward range");
            });
        }
        require(values.size() == 6, "Demo requires six packages");
        DailyShopConfig daily = blueprint.getDaily();
        require(daily.getCycleDurationSeconds() == 86400 && Math.floorMod(daily.getCycleAnchor() + 25200, 86400) == 0,
                "Cycle must start at Vietnam midnight");
        require(daily.getPurchaseLimit() == 3 && daily.getSlotsCount() == 3, "Daily requires three slots and purchase limit three");
        require(daily.getDiscountPercentagesList().equals(List.of(10, 20, 30)), "Discounts must be 10/20/30");
        Set<String> slots = new HashSet<>();
        daily.getSlotsList().forEach(slot -> require(slot.getSlotId().matches("[A-Za-z0-9_-]{1,64}") && slots.add(slot.getSlotId()), "Invalid daily slot"));
        Set<String> candidates = new HashSet<>();
        daily.getCandidatesList().forEach(candidate -> require(values.containsKey(candidate.getPackageId()) && candidates.add(candidate.getPackageId()), "Invalid candidate"));
        require(candidates.size() >= 3, "Not enough daily candidates");
        slots.clear();
        Set<String> staticPackages = new HashSet<>();
        blueprint.getStaticShop().getSlotsList().forEach(slot -> require(values.containsKey(slot.getPackageId())
                && slot.getSlotId().matches("[A-Za-z0-9_-]{1,64}") && slots.add(slot.getSlotId()) && staticPackages.add(slot.getPackageId()), "Invalid static slot"));
        require(staticPackages.equals(values.keySet()), "Static must include all six packages");
        packages = Collections.unmodifiableMap(values);
    }

    public ShopPackage packageById(String id) { return packages.get(id); }
    public List<ShopPackage> packages() { return List.copyOf(packages.values()); }
    public static ShopCatalog load() {
        try (InputStream input = ShopCatalog.class.getResourceAsStream("/shop.textproto")) {
            if (input == null) throw new IllegalStateException("shop.textproto is missing");
            ShopBlueprint.Builder builder = ShopBlueprint.newBuilder();
            TextFormat.getParser().merge(new InputStreamReader(input, StandardCharsets.UTF_8), builder);
            return new ShopCatalog(builder.build());
        } catch (IOException malformed) { throw new IllegalStateException("Invalid shop configuration", malformed); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
