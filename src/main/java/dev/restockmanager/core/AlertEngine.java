package dev.restockmanager.core;

import java.util.*;

/**
 * Two independent alert systems with "only alert once" latching, per-rule cooldowns and regear detection.
 * Pure logic: time and inventory are passed in, so it is unit-testable without Minecraft.
 */
public class AlertEngine {
    public final List<TrackedRule> hotbarRules = new ArrayList<>();
    public final List<TrackedRule> lowKitRules = new ArrayList<>();
    public boolean globalEnabled = true;
    public boolean countOffhand = true;
    public int regearMinIncrease = 16;
    public boolean restockedNotice = true;
    // two independent notification streams; defaults: hotbar top-right, /rg reminder bottom-left
    public final NotifChannel hotbarNotif = new NotifChannel(0.985, 0.03);
    public final NotifChannel lowNotif = new NotifChannel(0.015, 0.97);
    // notification look shared by both
    public boolean notifIcon = true;
    public int notifSeconds = 4;
    public int notifScale = 100, textScale = 100, notifAlpha = 100;   // percent
    public int notifAnim = 0;                                          // 0 fade, 1 slide, 2 pop, 3 minimal
    public int colBg = 0x000000, colBorder = 0x2A2A3A, colText = 0xF2F6FA;
    public int colHotbar = 0x36A9E8, colLow = 0xF5A623, colOk = 0x3DDC84;
    // live inventory HUD
    public boolean hudEnabled = false, hudWarn = true, hudNames = true;
    public int hudScale = 100;
    public double hudX = 0.01, hudY = 0.3;
    public int colWarn = 0xFF5A5A;
    public boolean useGlobalSound = false;
    public String globalSound = "minecraft:block.note_block.pling";

    public void resetStyle() {
        notifScale = 100; textScale = 100; notifAlpha = 100; notifAnim = 0; notifIcon = true; notifSeconds = 4;
        colBg = 0x000000; colBorder = 0x2A2A3A; colText = 0xF2F6FA;
        colHotbar = 0x36A9E8; colLow = 0xF5A623; colOk = 0x3DDC84; colWarn = 0xFF5A5A;
    }

    private final Set<String> hotbarLatched = new HashSet<>();
    private final Set<String> lowLatched = new HashSet<>();
    private final Map<String, Long> lastFired = new HashMap<>();
    private final Map<String, Integer> lastTotals = new HashMap<>();

    public void reset() { hotbarLatched.clear(); lowLatched.clear(); lastFired.clear(); lastTotals.clear(); }

    private int measureHotbar(Inventory inv, TrackedRule r) {
        Inventory.Counts c = inv.count(r.matchers);
        return r.countStacks ? c.hotbarStacks() : c.hotbar();
    }

    private int measureTotal(Inventory inv, TrackedRule r) {
        return r.countStacks ? inv.stackEquivalent(r.matchers, countOffhand) : inv.count(r.matchers).total(countOffhand);
    }

    /** True when this low-kit rule's total supply is at or below its threshold (used by the HUD warning colour). */
    public boolean lowTotal(Inventory inv, TrackedRule r) { return r.enabled && measureTotal(inv, r) <= r.threshold; }

    /** True when this hotbar rule's hotbar count is at or below its threshold. */
    public boolean lowHotbar(Inventory inv, TrackedRule r) { return r.enabled && measureHotbar(inv, r) <= r.threshold; }

    private boolean cooldownOk(String id, TrackedRule r, long now) {
        Long last = lastFired.get(id);
        return r.cooldownMs <= 0 || last == null || now - last >= r.cooldownMs;
    }

    public List<Alert> update(Inventory inv, long nowMs) {
        List<Alert> out = new ArrayList<>();
        if (inv == null) return out;

        for (TrackedRule r : hotbarRules) {
            String id = "h:" + r.key;
            int qty = measureHotbar(inv, r);
            boolean low = r.enabled && qty <= r.threshold;
            if (!low) { hotbarLatched.remove(id); continue; }
            boolean replacement = inv.count(r.matchers).main() > 0;
            if (!replacement) continue; // nothing to restock from: no alert, latch untouched
            if (!hotbarLatched.contains(id) && globalEnabled && cooldownOk(id, r, nowMs)) {
                hotbarLatched.add(id);
                lastFired.put(id, nowMs);
                out.add(new Alert(Alert.Type.HOTBAR_RESTOCK, r, qty,
                        format(r, qty, "Your {item} are low in your hotbar! Restock from inventory.")));
            }
        }

        for (TrackedRule r : lowKitRules) {
            String id = "l:" + r.key;
            int total = measureTotal(inv, r);
            int raw = inv.count(r.matchers).total(countOffhand);
            Integer prev = lastTotals.put(id, raw);
            boolean low = r.enabled && total <= r.threshold;
            if (!low) {
                boolean was = lowLatched.remove(id);
                if (was && prev != null && raw - prev >= regearMinIncrease && globalEnabled && restockedNotice)
                    out.add(new Alert(Alert.Type.RESTOCKED, r, raw, "Kit Restocked! " + r.displayName + ": " + raw));
                continue;
            }
            if (!lowLatched.contains(id) && globalEnabled && cooldownOk(id, r, nowMs)) {
                lowLatched.add(id);
                lastFired.put(id, nowMs);
                out.add(new Alert(Alert.Type.LOW_KIT, r, total,
                        format(r, total, "You're low on {item} ({count} {unit} remaining). Consider using /rg.")));
            }
        }
        out.sort(Comparator.comparingInt((Alert a) -> -a.rule().priority));
        return out;
    }

    private static String format(TrackedRule r, int qty, String fallback) {
        String t = (r.message == null || r.message.isBlank()) ? fallback : r.message;
        return t.replace("{item}", r.displayName)
                .replace("{count}", Integer.toString(qty))
                .replace("{unit}", r.countStacks ? (qty == 1 ? "stack" : "stacks") : "items");
    }
}
