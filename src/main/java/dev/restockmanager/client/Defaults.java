package dev.restockmanager.client;

import dev.restockmanager.core.*;
import java.util.List;
import java.util.Set;

final class Defaults {
    private static ItemMatcher item(String... ids) { return new ItemMatcher(Set.of(ids), null, null); }
    private static ItemMatcher pot(Set<String> effects, String... ids) { return new ItemMatcher(Set.of(ids), effects, null); }
    private static TrackedRule rule(String key, String name, ItemMatcher m, int threshold, String icon) {
        TrackedRule r = new TrackedRule(key, name, List.of(m), threshold);
        r.icon = icon;
        return r;
    }

    static void populate(AlertEngine e) {
        Object[][] base = {
            {"ender_pearls", "Ender Pearls", "minecraft:ender_pearl", 5, 16},
            {"end_crystals", "End Crystals", "minecraft:end_crystal", 8, 16},
            {"obsidian", "Obsidian", "minecraft:obsidian", 8, 16},
            {"glowstone", "Glowstone", "minecraft:glowstone", 8, 16},
            {"golden_apples", "Golden Apples", "minecraft:golden_apple", 4, 5},
        };
        for (Object[] b : base) {
            String id = (String) b[2];
            e.hotbarRules.add(rule((String) b[0], (String) b[1], item(id), (Integer) b[3], id));
            e.lowKitRules.add(rule((String) b[0], (String) b[1], item(id), (Integer) b[4], id));
        }
        e.lowKitRules.add(rule("potions", "Potions", item("minecraft:potion", "minecraft:splash_potion"), 2, "minecraft:potion"));
        e.lowKitRules.add(rule("xp_bottles", "Bottles of Enchanting", item("minecraft:experience_bottle"), 16, "minecraft:experience_bottle"));
        e.lowKitRules.add(rule("slow_falling_arrows", "Slow Falling Arrows",
            pot(Set.of("minecraft:slow_falling", "minecraft:long_slow_falling"), "minecraft:tipped_arrow"), 8,
            "minecraft:tipped_arrow#minecraft:slow_falling"));
        e.lowKitRules.add(rule("fire_resistance", "Fire Resistance Arrows",
            pot(Set.of("minecraft:fire_resistance", "minecraft:long_fire_resistance"), "minecraft:tipped_arrow"), 8,
            "minecraft:tipped_arrow#minecraft:fire_resistance"));
    }
}
