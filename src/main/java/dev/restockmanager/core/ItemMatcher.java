package dev.restockmanager.core;

import java.util.List;
import java.util.Set;

/**
 * Decides whether a stack belongs to a tracked category.
 * itemIds: any of these item ids matches. effectIds: if non-empty, the stack's effect must be one of them.
 * nameContains: if non-empty, the custom name must contain it (case-insensitive) - for custom server items.
 */
public record ItemMatcher(Set<String> itemIds, Set<String> effectIds, String nameContains) {
    public ItemMatcher {
        itemIds = itemIds == null ? Set.of() : Set.copyOf(itemIds);
        effectIds = effectIds == null ? Set.of() : Set.copyOf(effectIds);
        nameContains = nameContains == null ? "" : nameContains;
    }

    public boolean matches(StackInfo s) {
        if (s == null || s.count() <= 0 || s.itemId() == null) return false;
        if (!itemIds.contains(s.itemId())) return false;
        if (!effectIds.isEmpty() && (s.effectId() == null || !effectIds.contains(s.effectId()))) return false;
        if (!nameContains.isEmpty()) {
            if (s.customName() == null) return false;
            if (!s.customName().toLowerCase().contains(nameContains.toLowerCase())) return false;
        }
        return true;
    }

    public static boolean any(List<ItemMatcher> ms, StackInfo s) {
        for (ItemMatcher m : ms) if (m.matches(s)) return true;
        return false;
    }
}
