package dev.restockmanager.core;

/** Plain, Minecraft-free snapshot of one inventory stack. effectId/customName may be null. */
public record StackInfo(String itemId, String effectId, String customName, int count, int maxStack) {
    public StackInfo {
        if (count < 0) count = 0;
        if (maxStack < 1) maxStack = 1;
    }
    public static StackInfo of(String itemId, int count, int maxStack) {
        return new StackInfo(itemId, null, null, count, maxStack);
    }
}
