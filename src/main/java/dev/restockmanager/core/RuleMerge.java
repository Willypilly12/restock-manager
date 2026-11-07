package dev.restockmanager.core;

/** Carries a user's own choices from an old rule onto a fresh default rule (used when the default item set changes). */
public final class RuleMerge {
    private RuleMerge() { }

    public static void copyUserSettings(TrackedRule from, TrackedRule to) {
        to.threshold = from.threshold;
        to.enabled = from.enabled;
        to.countStacks = from.countStacks;
        to.message = from.message == null ? "" : from.message;
        to.cooldownMs = from.cooldownMs;
        to.sound = from.sound == null ? to.sound : from.sound;
        to.volume = from.volume;
        to.soundEnabled = from.soundEnabled;
        to.priority = from.priority;
        to.showInHud = from.showInHud;
    }
}
