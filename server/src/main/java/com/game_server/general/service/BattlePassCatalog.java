package com.game_server.general.service;

import com.game.server.proto.BattlePassBlueprintContract.*;
import com.game.server.proto.BattlePassModel.*;
import com.google.protobuf.TextFormat;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;

/** Shared definitions; Player progress never changes this catalog. */
public final class BattlePassCatalog {
    private final BattlePassBlueprint blueprint;
    private final Map<String, BattlePassNodeConfig> nodes = new LinkedHashMap<>();
    private final Map<String, HeroDefinition> heroes = new LinkedHashMap<>();
    private final Map<HeroRarity, Long> compensation = new EnumMap<>(HeroRarity.class);

    public BattlePassCatalog(BattlePassBlueprint blueprint) {
        this.blueprint = Objects.requireNonNull(blueprint);
        // Reserve '-' plus up to 19 decimal digits for the generated season index.
        require(id(blueprint.getId()) && blueprint.getId().length() <= 108, "Battle Pass template ID must have 1-108 characters");
        require(blueprint.getScheduleOrigin() > 0 && blueprint.getDurationSeconds() > 0, "Invalid Battle Pass schedule");
        try { Math.addExact(blueprint.getScheduleOrigin(), blueprint.getDurationSeconds()); }
        catch (ArithmeticException overflow) { throw new IllegalArgumentException("Invalid Battle Pass schedule", overflow); }
        require(blueprint.getExpPerNode() == 500, "Each node requires 500 EXP");
        require(blueprint.getPremiumPriceGems() > 0, "Invalid Premium price");
        for (HeroCompensationRule rule : blueprint.getCompensationList()) {
            require(validRarity(rule.getRarity()) && rule.getGems() > 0
                    && compensation.putIfAbsent(rule.getRarity(), rule.getGems()) == null, "Invalid Hero compensation");
        }
        require(compensation.keySet().equals(Set.of(HeroRarity.BASIC, HeroRarity.EPIC, HeroRarity.LEGENDARY)), "Missing rarity compensation");
        for (HeroDefinition hero : blueprint.getHeroesList()) {
            require(id(hero.getHeroId()) && validRarity(hero.getRarity())
                    && heroes.putIfAbsent(hero.getHeroId(), hero) == null, "Invalid Hero definition");
        }
        for (BattlePassNodeConfig node : blueprint.getNodesList()) {
            require(id(node.getNodeId()) && nodes.putIfAbsent(node.getNodeId(), node) == null, "Invalid node ID");
            BattlePassRewardProto reward = node.getReward();
            switch (reward.getRewardCase()) {
                case RESOURCE:
                    require(Set.of("gold", "gem", "xp").contains(reward.getResource().getResourceId())
                            && reward.getResource().getQuantity() > 0, "Invalid Resource reward");
                    break;
                case HERO_ID: require(heroes.containsKey(reward.getHeroId()), "Unknown Hero reward"); break;
                default: throw new IllegalArgumentException("Each node requires exactly one reward");
            }
        }
        require(!nodes.isEmpty(), "Battle Pass needs nodes");
    }

    public BattlePassBlueprint blueprint() { return blueprint; }
    public BattlePassNodeConfig node(String id) { return nodes.get(id); }
    public HeroDefinition hero(String id) { return heroes.get(id); }
    public long compensation(HeroRarity rarity) { return compensation.get(rarity); }
    public long maxExp() { return nodes.size() * 500L; }
    public static boolean id(String value) { return value != null && value.matches("[A-Za-z0-9_-]{1,128}"); }

    public Pass active(Clock clock) {
        long now = clock.instant().getEpochSecond();
        if (now < blueprint.getScheduleOrigin())
            throw new GameException(409, "BATTLE_PASS_NOT_ACTIVE", "Battle Pass has not started");
        long index = (now - blueprint.getScheduleOrigin()) / blueprint.getDurationSeconds();
        long start = Math.addExact(blueprint.getScheduleOrigin(), Math.multiplyExact(index, blueprint.getDurationSeconds()));
        return new Pass(blueprint.getId() + "-" + index, start, Math.addExact(start, blueprint.getDurationSeconds()));
    }

    public static BattlePassCatalog load() {
        try (InputStream input = BattlePassCatalog.class.getResourceAsStream("/battle_pass.textproto")) {
            if (input == null) throw new IllegalStateException("battle_pass.textproto is missing");
            BattlePassBlueprint.Builder builder = BattlePassBlueprint.newBuilder();
            TextFormat.getParser().merge(new InputStreamReader(input, StandardCharsets.UTF_8), builder);
            return new BattlePassCatalog(builder.build());
        } catch (IOException malformed) { throw new IllegalStateException("Invalid Battle Pass configuration", malformed); }
    }
    private static boolean validRarity(HeroRarity rarity) {
        return rarity == HeroRarity.BASIC || rarity == HeroRarity.EPIC || rarity == HeroRarity.LEGENDARY;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
    public static final class Pass {
        public final String id;
        public final long start, end;
        private Pass(String id, long start, long end) { this.id = id; this.start = start; this.end = end; }
    }
}
