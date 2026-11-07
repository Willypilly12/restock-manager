package dev.restockmanager.client;

import dev.restockmanager.core.*;
import java.util.*;

/** Lives in the client package to reach package-private Defaults (which has no Minecraft imports). */
public class DefaultsTests {
    static int fails = 0;
    static void check(boolean ok, String name) { System.out.println((ok ? "PASS " : "FAIL ") + name); if (!ok) fails++; }
    static StackInfo arrow(String effect, int n) { return new StackInfo("minecraft:tipped_arrow", effect, null, n, 64); }
    static StackInfo potion(String effect) { return new StackInfo("minecraft:potion", effect, null, 1, 1); }
    static Inventory inv(StackInfo... hot) {
        List<StackInfo> h = new ArrayList<>(Arrays.asList(hot)); while (h.size() < 9) h.add(null);
        List<StackInfo> m = new ArrayList<>(); while (m.size() < 27) m.add(null);
        return new Inventory(h, m, null);
    }
    static TrackedRule rule(AlertEngine e, String key) {
        for (TrackedRule r : e.lowKitRules) if (r.key.equals(key)) return r;
        throw new IllegalStateException(key);
    }

    public static void main(String[] a) {
        AlertEngine e = new AlertEngine(); Defaults.populate(e);
        TrackedRule fire = rule(e, "fire_resistance"), slow = rule(e, "slow_falling_arrows"), pots = rule(e, "potions");
        Inventory i = inv(arrow("minecraft:fire_resistance", 10), arrow("minecraft:long_fire_resistance", 5),
                arrow("minecraft:slow_falling", 7), arrow("minecraft:harming", 3), potion("minecraft:fire_resistance"), potion("minecraft:healing"));
        check(i.count(fire.matchers).hotbar() == 15, "fire resistance rule counts only fire resistance ARROWS (10+5)");
        check(i.count(slow.matchers).hotbar() == 7, "slow falling rule counts only slow falling arrows");
        check(i.count(pots.matchers).hotbar() == 2, "potions rule counts potions, not arrows");
        check(fire.displayName.equals("Fire Resistance Arrows"), "fire resistance rule is named for arrows");
        check("minecraft:tipped_arrow#minecraft:slow_falling".equals(slow.icon) && "minecraft:tipped_arrow#minecraft:fire_resistance".equals(fire.icon), "arrow icons carry their potion effect");
        for (TrackedRule r : e.lowKitRules) check(r.icon != null, "icon set for " + r.key);

        // migration helper: a user's old settings land on the new default rule
        TrackedRule old = new TrackedRule("fire_resistance", "Fire Resistance", List.of(), 3);
        old.enabled = false; old.message = "mine"; old.volume = 0.4f; old.cooldownMs = 9000; old.sound = "minecraft:entity.player.levelup";
        RuleMerge.copyUserSettings(old, fire);
        check(fire.threshold == 3 && !fire.enabled && fire.message.equals("mine") && fire.volume == 0.4f && fire.cooldownMs == 9000, "user settings carried onto new default rule");
        check(fire.displayName.equals("Fire Resistance Arrows") && fire.icon.endsWith("fire_resistance"), "merge keeps the new name, icon and matcher");

        System.out.println(fails == 0 ? "ALL DEFAULTS TESTS PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
