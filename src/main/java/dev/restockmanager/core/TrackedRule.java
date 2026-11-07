package dev.restockmanager.core;

import java.util.List;

/** Settings for one tracked category in one alert system (hotbar or low-kit). */
public class TrackedRule {
    public String key;
    public String displayName;
    public List<ItemMatcher> matchers;
    public int threshold;
    public boolean enabled = true;
    public boolean countStacks = false;
    public String message = "";            // custom text; supports {item} {count} {unit}
    public long cooldownMs = 0;
    public String sound = "minecraft:block.note_block.pling";
    public float volume = 1.0f;
    public boolean soundEnabled = true;
    public int priority = 0;
    public boolean showInHud = true;
    public String icon;                    // item id shown in menu/notifications

    /** For JSON loading, so field defaults apply to settings missing from a file. */
    public TrackedRule() { }

    public TrackedRule(String key, String displayName, List<ItemMatcher> matchers, int threshold) {
        this.key = key; this.displayName = displayName; this.matchers = matchers; this.threshold = threshold;
    }
}
