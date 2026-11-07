package dev.restockmanager.core;

import java.util.*;

public class CoreTests {
    static int fails = 0;
    static void check(boolean ok, String name) { System.out.println((ok ? "PASS " : "FAIL ") + name); if (!ok) fails++; }

    static final ItemMatcher PEARL = new ItemMatcher(Set.of("minecraft:ender_pearl"), null, null);
    static final ItemMatcher FIRE_POT = new ItemMatcher(Set.of("minecraft:potion", "minecraft:splash_potion"),
            Set.of("minecraft:fire_resistance", "minecraft:long_fire_resistance"), null);
    static final ItemMatcher ANY_POT = new ItemMatcher(Set.of("minecraft:potion", "minecraft:splash_potion"), null, null);

    static Inventory inv(StackInfo[] hot, StackInfo[] main, StackInfo off) {
        List<StackInfo> h = new ArrayList<>(Arrays.asList(hot));
        while (h.size() < 9) h.add(null);
        List<StackInfo> m = new ArrayList<>(Arrays.asList(main));
        while (m.size() < 27) m.add(null);
        return new Inventory(h, m, off);
    }
    static StackInfo pearls(int n) { return StackInfo.of("minecraft:ender_pearl", n, 16); }

    public static void main(String[] a) {
        // counting: split stacks, empty slots, hotbar/main/offhand separation
        Inventory i1 = inv(new StackInfo[]{pearls(3), null, pearls(2)}, new StackInfo[]{null, pearls(16), pearls(8)}, pearls(1));
        Inventory.Counts c = i1.count(List.of(PEARL));
        check(c.hotbar() == 5 && c.main() == 24 && c.offhand() == 1, "counts split across slots");
        check(c.total(true) == 30 && c.total(false) == 29, "offhand toggle");
        check(i1.stackEquivalent(List.of(PEARL), true) == 2, "stack-equivalent rounds up (30/16 -> 2)");

        // potion variants must not leak between categories
        StackInfo fire = new StackInfo("minecraft:potion", "minecraft:fire_resistance", null, 1, 1);
        StackInfo heal = new StackInfo("minecraft:potion", "minecraft:healing", null, 1, 1);
        Inventory i2 = inv(new StackInfo[]{fire, heal, heal}, new StackInfo[]{}, null);
        check(i2.count(List.of(FIRE_POT)).hotbar() == 1, "fire resistance matcher ignores healing");
        check(i2.count(List.of(ANY_POT)).hotbar() == 3, "any-potion matcher counts all");
        check(!FIRE_POT.matches(new StackInfo("minecraft:potion", null, null, 1, 1)), "no-effect potion not fire res");
        ItemMatcher named = new ItemMatcher(Set.of("minecraft:paper"), null, "regear");
        check(named.matches(new StackInfo("minecraft:paper", null, "Regear Token", 1, 64)) && !named.matches(StackInfo.of("minecraft:paper", 1, 64)), "custom-name matching");

        // hotbar alert: fire once, latch, re-arm, no replacement -> silent
        AlertEngine e = new AlertEngine();
        TrackedRule hr = new TrackedRule("pearl", "Ender Pearls", List.of(PEARL), 5);
        e.hotbarRules.add(hr);
        Inventory lowWithSpare = inv(new StackInfo[]{pearls(5)}, new StackInfo[]{pearls(16), pearls(16)}, null);
        check(e.update(lowWithSpare, 0).size() == 1, "hotbar alert fires at threshold with spare in inventory");
        check(e.update(lowWithSpare, 1000).isEmpty(), "no spam while still low");
        Inventory ok = inv(new StackInfo[]{pearls(16)}, new StackInfo[]{}, null);
        check(e.update(ok, 2000).isEmpty(), "above threshold silent");
        check(e.update(lowWithSpare, 3000).size() == 1, "re-arms after rising above then dropping");
        AlertEngine e2 = new AlertEngine(); e2.hotbarRules.add(new TrackedRule("pearl", "Ender Pearls", List.of(PEARL), 5));
        check(e2.update(inv(new StackInfo[]{pearls(2)}, new StackInfo[]{}, null), 0).isEmpty(), "no alert without replacement items");
        check(e2.update(lowWithSpare, 10).size() == 1, "alert once replacement appears");
        e.globalEnabled = false; e.reset();
        check(e.update(lowWithSpare, 0).isEmpty(), "global toggle off silences alerts");

        // low-kit alert, custom message, stacks mode, cooldown
        AlertEngine k = new AlertEngine();
        TrackedRule lr = new TrackedRule("pearl", "Ender Pearls", List.of(PEARL), 1);
        lr.countStacks = true; lr.cooldownMs = 5000;
        k.lowKitRules.add(lr);
        Inventory oneStack = inv(new StackInfo[]{pearls(10)}, new StackInfo[]{}, null);
        List<Alert> r1 = k.update(oneStack, 0);
        check(r1.size() == 1 && r1.get(0).message().equals("You're low on Ender Pearls (1 stack remaining). Consider using /rg."), "low-kit message with stacks");
        lr.message = "Pearls low: {count}";
        k.update(inv(new StackInfo[]{pearls(64)}, new StackInfo[]{pearls(64)}, null), 1000);
        check(k.update(oneStack, 2000).isEmpty(), "cooldown suppresses re-alert within window");
        k.update(inv(new StackInfo[]{pearls(64)}, new StackInfo[]{pearls(64)}, null), 3000);
        List<Alert> r2 = k.update(oneStack, 9000);
        check(r2.size() == 1 && r2.get(0).message().equals("Pearls low: 1"), "custom message after cooldown");

        // regear detection: big rise fires RESTOCKED; small pickup does not; non-latched rise does not
        AlertEngine g = new AlertEngine();
        g.lowKitRules.add(new TrackedRule("pearl", "Ender Pearls", List.of(PEARL), 5));
        g.update(inv(new StackInfo[]{pearls(3)}, new StackInfo[]{}, null), 0);
        List<Alert> rg = g.update(inv(new StackInfo[]{pearls(16), pearls(16), pearls(16), pearls(16)}, new StackInfo[]{}, null), 100);
        check(rg.size() == 1 && rg.get(0).type() == Alert.Type.RESTOCKED, "regear detected");
        AlertEngine g2 = new AlertEngine();
        g2.lowKitRules.add(new TrackedRule("pearl", "Ender Pearls", List.of(PEARL), 5));
        g2.update(inv(new StackInfo[]{pearls(3)}, new StackInfo[]{}, null), 0);
        check(g2.update(inv(new StackInfo[]{pearls(8)}, new StackInfo[]{}, null), 100).isEmpty(), "small pickup is not a regear");
        AlertEngine g3 = new AlertEngine();
        g3.lowKitRules.add(new TrackedRule("pearl", "Ender Pearls", List.of(PEARL), 5));
        g3.update(inv(new StackInfo[]{pearls(10)}, new StackInfo[]{}, null), 0);
        check(g3.update(inv(new StackInfo[]{pearls(16), pearls(16), pearls(16)}, new StackInfo[]{}, null), 50).isEmpty(), "rise without prior low alert is not 'restocked'");

        // robustness
        check(new AlertEngine().update(null, 0).isEmpty(), "null inventory is safe");
        check(StackInfo.of("x", -4, 0).count() == 0, "negative count/maxStack sanitised");

        // share codes
        String json = "{\"version\":2,\"hotbarRules\":[{\"key\":\"x\",\"message\":\"caf\u00e9 {item}\"}]}";
        String code = ShareCode.encode(json);
        check(code.startsWith("RSM1:") && ShareCode.decode(code).equals(json), "share code round-trips (incl. non-ASCII)");
        check(ShareCode.decode("  " + code.substring(0, 20) + "\n  " + code.substring(20) + " ").equals(json), "share code tolerates pasted whitespace/line breaks");
        boolean bad1 = false, bad2 = false, bad3 = false, bad4 = false;
        try { ShareCode.decode("hello"); } catch (IllegalArgumentException ex) { bad1 = true; }
        try { ShareCode.decode("RSM1:!!!notbase64!!!"); } catch (IllegalArgumentException ex) { bad2 = true; }
        try { ShareCode.decode("RSM1:" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("not gzip".getBytes())); } catch (IllegalArgumentException ex) { bad3 = true; }
        try { ShareCode.decode(null); } catch (IllegalArgumentException ex) { bad4 = true; }
        check(bad1 && bad2 && bad3 && bad4, "invalid codes are rejected cleanly");
        String big = "a".repeat(450_000);
        boolean bomb = false;
        try { ShareCode.decode(ShareCode.encode(big)); } catch (IllegalArgumentException ex) { bomb = true; }
        check(bomb, "oversized decoded data (zip bomb) is rejected");
        AlertEngine st = new AlertEngine(); st.notifScale = 200; st.colBg = 0x123456; st.notifAnim = 2; st.resetStyle();
        check(st.notifScale == 100 && st.colBg == 0 && st.notifAnim == 0, "resetStyle restores style defaults");

        // HUD low flags + notification channels
        AlertEngine hu = new AlertEngine();
        TrackedRule hlow = new TrackedRule("pearl", "Ender Pearls", List.of(PEARL), 5);
        hu.hotbarRules.add(hlow); hu.lowKitRules.add(new TrackedRule("pearl", "Ender Pearls", List.of(PEARL), 20));
        Inventory hv = inv(new StackInfo[]{pearls(4)}, new StackInfo[]{pearls(10)}, null);
        check(hu.lowHotbar(hv, hlow) && hu.lowTotal(hv, hu.lowKitRules.get(0)), "HUD flags: hotbar 4<=5 and total 14<=20 are low");
        Inventory hv2 = inv(new StackInfo[]{pearls(16)}, new StackInfo[]{pearls(16)}, null);
        check(!hu.lowHotbar(hv2, hlow) && !hu.lowTotal(hv2, hu.lowKitRules.get(0)), "HUD flags: plentiful supply is not low");
        hlow.enabled = false;
        check(!hu.lowHotbar(hv, hlow), "disabled item never shows as low");
        check(hu.hotbarNotif.x > 0.9 && hu.hotbarNotif.y < 0.2 && hu.lowNotif.x < 0.1 && hu.lowNotif.y > 0.8, "defaults: hotbar top-right, /rg bottom-left");
        NotifChannel nc = new NotifChannel(); nc.x = 7; nc.y = -2; nc.mode = 9; nc.clampValues();
        check(nc.x == 1 && nc.y == 0 && nc.mode == 3, "channel values clamp");
        check(new TrackedRule().showInHud, "items show in HUD by default");

        System.out.println(fails == 0 ? "ALL TESTS PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
