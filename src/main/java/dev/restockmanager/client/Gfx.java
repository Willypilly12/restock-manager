package dev.restockmanager.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Drawing helpers. All matrix-stack use goes through push/pop/translate/scale so there is one place to fix per API change. */
final class Gfx {
    static void push(DrawContext c) { c.getMatrices().pushMatrix(); }
    static void pop(DrawContext c) { c.getMatrices().popMatrix(); }
    static void translate(DrawContext c, float x, float y) { c.getMatrices().translate(x, y); }
    static void scale(DrawContext c, float s) { c.getMatrices().scale(s, s); }

    static int al(int argb, float a) {
        int A = Math.max(0, Math.min(255, (int) ((argb >>> 24) * a)));
        return (A << 24) | (argb & 0xFFFFFF);
    }

    static int mix(int rgbA, int rgbB, float t) {
        int r = (int) (((rgbA >> 16) & 255) * (1 - t) + ((rgbB >> 16) & 255) * t);
        int g = (int) (((rgbA >> 8) & 255) * (1 - t) + ((rgbB >> 8) & 255) * t);
        int b = (int) ((rgbA & 255) * (1 - t) + (rgbB & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    static int inset(int r, int i) {
        double d = r - i - 0.5;
        return r - (int) Math.round(Math.sqrt(Math.max(0, r * r - d * d)));
    }

    static void rr(DrawContext c, int x1, int y1, int x2, int y2, int r, int col) {
        r = Math.max(0, Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            c.fill(x1 + in, y1 + i, x2 - in, y1 + i + 1, col);
            c.fill(x1 + in, y2 - i - 1, x2 - in, y2 - i, col);
        }
        c.fill(x1, y1 + r, x2, y2 - r, col);
    }

    /** Rounded rect with a vertical gradient from topRgb to botRgb. */
    static void grad(DrawContext c, int x1, int y1, int x2, int y2, int r, int topRgb, int botRgb, float a, double power) {
        int h = y2 - y1;
        for (int row = 0; row < h; row++) {
            float t = (float) Math.pow((float) row / Math.max(1, h - 1), power);
            int col = al(0xFF000000 | mix(topRgb, botRgb, t), a);
            int in = 0;
            if (row < r) in = inset(r, row);
            else if (row >= h - r) in = inset(r, h - 1 - row);
            c.fill(x1 + in, y1 + row, x2 - in, y1 + row + 1, col);
        }
    }

    /** Text with optional scale. Skips nearly transparent text: Minecraft draws alpha below 4 as fully opaque. */
    static void text(DrawContext c, TextRenderer tr, String s, int x, int y, int rgb, float alpha, float ts) {
        int a8 = (int) (Math.max(0f, Math.min(1f, alpha)) * 255);
        if (a8 < 8) return;
        int col = (a8 << 24) | (rgb & 0xFFFFFF);
        if (Math.abs(ts - 1f) < 0.01f) { c.drawText(tr, Text.literal(s), x, y, col, false); return; }
        push(c);
        translate(c, x, y);
        scale(c, ts);
        c.drawText(tr, Text.literal(s), 0, 0, col, false);
        pop(c);
    }
}
