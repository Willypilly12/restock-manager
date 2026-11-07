package dev.restockmanager.client;

import dev.restockmanager.core.AlertEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Live inventory HUD: one row per tracked item with its icon, name, hotbar count and total.
 * With "warning colour" on, a low item's name and numbers use the warning colour.
 * Rows are refreshed from the inventory scan (every few ticks), never rescanned while drawing.
 */
final class Hud {
    /** low = total supply at/below the Low Kit threshold; hotbarLow = hotbar count at/below the Hotbar Alert threshold. */
    record Row(String icon, String name, int hotbar, int total, boolean low, boolean hotbarLow) { }

    private static volatile List<Row> rows = List.of();
    private static final int ROW = 18, PAD = 6;

    static void set(List<Row> r) { rows = r == null ? List.of() : r; }

    static List<Row> sample() {
        return List.of(
            new Row("minecraft:ender_pearl", "Ender Pearls", 4, 20, false, true),
            new Row("minecraft:end_crystal", "End Crystals", 32, 96, false, false),
            new Row("minecraft:golden_apple", "Golden Apples", 0, 3, true, true));
    }

    static float scale(AlertEngine e) { return Math.max(50, Math.min(250, e.hudScale)) / 100f; }

    private static String nums(Row r) { return r.hotbar() + " | " + r.total(); }

    static int width(TextRenderer tr, AlertEngine e, List<Row> list) {
        int nameW = 0, numW = tr.getWidth("0 | 0");
        for (Row r : list) { nameW = Math.max(nameW, tr.getWidth(r.name())); numW = Math.max(numW, tr.getWidth(nums(r))); }
        return PAD + 22 + (e.hudNames ? nameW + 10 : 0) + numW + PAD;
    }

    static int height(List<Row> list) { return PAD * 2 + Math.max(1, list.size()) * ROW - 2; }

    /** Draws the HUD with its top-left at (x, y), at scale s. mul dims it (used by the mover for a disabled HUD). */
    static void draw(DrawContext c, TextRenderer tr, AlertEngine e, List<Row> list, int x, int y, float s, float mul) {
        if (list.isEmpty()) return;
        float a = Math.max(0.1f, Math.min(1f, e.notifAlpha / 100f)) * mul;
        int w = width(tr, e, list), h = height(list);
        int bg = e.colBg & 0xFFFFFF, border = e.colBorder & 0xFFFFFF, txt = e.colText & 0xFFFFFF, warn = e.colWarn & 0xFFFFFF;
        int nameW = 0;
        for (Row r : list) nameW = Math.max(nameW, tr.getWidth(r.name()));
        Gfx.push(c);
        Gfx.translate(c, x, y);
        Gfx.scale(c, s);
        Gfx.rr(c, -1, -1, w + 1, h + 1, 7, Gfx.al(0xFF000000 | border, a));
        Gfx.grad(c, 0, 0, w, h, 6, bg, Gfx.mix(bg, e.colHotbar & 0xFFFFFF, 0.16f), a, 1.6);
        for (int i = 0; i < list.size(); i++) {
            Row r = list.get(i);
            int ry = PAD + i * ROW;
            boolean warnNow = e.hudWarn && r.low();
            if (a > 0.5f) Icons.draw(c, r.icon(), PAD, ry);
            int tx = PAD + 22;
            if (e.hudNames) {
                Gfx.text(c, tr, r.name(), tx, ry + 4, warnNow ? warn : txt, a, 1f);
                tx += nameW + 10;
            }
            int hotCol = (e.hudWarn && (r.low() || r.hotbarLow())) ? warn : txt;
            int totCol = warnNow ? warn : (r.total() == 0 ? 0x8590A6 : txt);
            String hot = Integer.toString(r.hotbar());
            Gfx.text(c, tr, hot, tx, ry + 4, hotCol, a, 1f);
            tx += tr.getWidth(hot);
            Gfx.text(c, tr, " | ", tx, ry + 4, 0x8590A6, a, 1f);
            tx += tr.getWidth(" | ");
            Gfx.text(c, tr, Integer.toString(r.total()), tx, ry + 4, totCol, a, 1f);
        }
        Gfx.pop(c);
    }

    static void render(DrawContext c, AlertEngine e) {
        if (!e.hudEnabled) return;
        List<Row> list = rows;
        if (list.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        if (tr == null || mc.getWindow() == null) return;
        float s = scale(e);
        int w = Math.round(width(tr, e, list) * s), h = Math.round(height(list) * s);
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int x = (int) Math.round(Math.max(0, Math.min(1, e.hudX)) * Math.max(0, sw - w));
        int y = (int) Math.round(Math.max(0, Math.min(1, e.hudY)) * Math.max(0, sh - h));
        draw(c, tr, e, list, x, y, s, 1f);
    }
}
