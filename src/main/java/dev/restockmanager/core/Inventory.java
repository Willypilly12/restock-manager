package dev.restockmanager.core;

import java.util.List;

/** Snapshot of the player's inventory: hotbar (9), main (27), offhand. Null entries = empty slots. */
public record Inventory(List<StackInfo> hotbar, List<StackInfo> main, StackInfo offhand) {
    public record Counts(int hotbar, int main, int offhand, int hotbarStacks, int mainStacks) {
        public int total(boolean includeOffhand) { return hotbar + main + (includeOffhand ? offhand : 0); }
    }

    public Counts count(List<ItemMatcher> matchers) {
        int h = 0, m = 0, hs = 0, ms = 0, o = 0;
        for (StackInfo s : hotbar) if (ItemMatcher.any(matchers, s)) { h += s.count(); hs++; }
        for (StackInfo s : main)   if (ItemMatcher.any(matchers, s)) { m += s.count(); ms++; }
        if (ItemMatcher.any(matchers, offhand)) o = offhand.count();
        return new Counts(h, m, o, hs, ms);
    }

    /** Total items divided by max stack size (rounded up). */
    public int stackEquivalent(List<ItemMatcher> matchers, boolean includeOffhand) {
        int items = 0, max = 0;
        for (StackInfo s : hotbar) if (ItemMatcher.any(matchers, s)) { items += s.count(); max = Math.max(max, s.maxStack()); }
        for (StackInfo s : main)   if (ItemMatcher.any(matchers, s)) { items += s.count(); max = Math.max(max, s.maxStack()); }
        if (includeOffhand && ItemMatcher.any(matchers, offhand)) { items += offhand.count(); max = Math.max(max, offhand.maxStack()); }
        return max == 0 ? 0 : (items + max - 1) / max;
    }
}
