package dev.restockmanager.client;

import dev.restockmanager.core.ItemMatcher;
import dev.restockmanager.core.TrackedRule;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Item icons for rules. An icon is an item id, optionally followed by "#potion_effect_id"
 * (e.g. minecraft:tipped_arrow#minecraft:slow_falling) so tipped arrows show the right coloured tip.
 */
final class Icons {
    private static final Map<String, ItemStack> CACHE = new HashMap<>();

    static String of(TrackedRule r) {
        if (r == null) return null;
        if (r.icon != null && !r.icon.isBlank()) return r.icon;
        try {
            TreeSet<String> ids = new TreeSet<>();
            for (ItemMatcher m : r.matchers) ids.addAll(m.itemIds());
            return ids.isEmpty() ? null : ids.first();
        } catch (Exception e) { return null; }
    }

    private static ItemStack build(String spec) {
        String base = spec, effect = null;
        int h = spec.indexOf('#');
        if (h >= 0) { base = spec.substring(0, h); effect = spec.substring(h + 1); }
        Identifier bid = Identifier.tryParse(base);
        if (bid == null) return ItemStack.EMPTY;
        Item item = Registries.ITEM.get(bid);
        if (effect != null) {
            Identifier eid = Identifier.tryParse(effect);
            if (eid != null) {
                var entry = Registries.POTION.getEntry(eid);
                if (entry.isPresent()) return PotionContentsComponent.createStack(item, entry.get());
            }
        }
        return new ItemStack(item);
    }

    static void draw(DrawContext c, String spec, int x, int y) {
        if (spec == null) return;
        try {
            ItemStack st = CACHE.get(spec);
            if (st == null) { st = build(spec); CACHE.put(spec, st); }
            if (!st.isEmpty()) c.drawItem(st, x, y);
        } catch (Throwable ignored) { /* never crash on a bad icon */ }
    }
}
