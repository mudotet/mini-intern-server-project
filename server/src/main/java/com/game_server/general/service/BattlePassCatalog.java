package com.game_server.general.service;

import java.util.List;
import java.util.Map;

/** The demo's weekly pass and daily quest definitions. */
public final class BattlePassCatalog {
    public static final String NAME = "Emberbound Covenant";
    public static final int PREMIUM_GEMS = 50;
    public static final List<Milestone> MILESTONES = List.of(
            new Milestone(1, Map.of("gold", 100), Map.of("gem", 10)),
            new Milestone(2, Map.of("gem", 5), Map.of("gold", 300)),
            new Milestone(3, Map.of("xp", 150), Map.of("gem", 15)),
            new Milestone(4, Map.of("gold", 200), Map.of("xp", 400)),
            new Milestone(5, Map.of("gem", 10), Map.of("gold", 500)),
            new Milestone(6, Map.of("xp", 300), Map.of("gem", 25)));
    public static final List<Quest> QUESTS = List.of(
            new Quest("trade_once", "First Trade", "Purchase any shop offer.", 1, 100),
            new Quest("trade_three", "Seasoned Trader", "Purchase three shop offers.", 3, 150),
            new Quest("study", "Arcane Studies", "Receive 200 XP from shop purchases.", 200, 100));

    private BattlePassCatalog() {}

    public static final class Milestone {
        public final int step, requiredExpbp;
        public final Map<String, Integer> free, premium;
        private Milestone(int step, Map<String, Integer> free, Map<String, Integer> premium) {
            this.step = step;
            this.requiredExpbp = step * 100;
            this.free = free;
            this.premium = premium;
        }
    }
    public static final class Quest {
        public final String id, title, description;
        public final int target, expbpReward;
        private Quest(String id, String title, String description, int target, int expbpReward) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.target = target;
            this.expbpReward = expbpReward;
        }
    }
}
