package dev.restockmanager.client;

import dev.restockmanager.core.AlertEngine;
import dev.restockmanager.core.NotifChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Notification cards. Two independent streams (channel 0 = hotbar restock, channel 1 = /rg reminder),
 * each with its own position, on/off switch and display mode:
 * 0 icon only, 1 icon + count, 2 icon + count + short label, 3 full message.
 * Colours, size, text size, transparency and animation are shared (Style tab).
 */
final class Notifications {
    static final int KIND_HOTBAR = 0, KIND_LOW = 1, KIND_OK = 2;
    static final String[] MODE_NAMES = {"Icon only", "Icon + count", "Icon + count + label", "Full message"};

    /**
     * accent is 24-bit RGB. channel: 0 hotbar, 1 /rg reminder, -1 system (shown at the hotbar spot, always full).
     * count and label are null for system notices; compact modes need them.
     */
    record Notif(String title, String msg, int accent, String icon, long born, int channel, String count, String label) { }

    private static final List<Notif> LIVE = new ArrayList<>();

    static int accent(AlertEngine e, int kind) {
        return (kind == KIND_HOTBAR ? e.colHotbar : kind == KIND_LOW ? e.colLow : e.colOk) & 0xFFFFFF;
    }

    static NotifChannel channelOf(AlertEngine e, int channel) { return channel == 1 ? e.lowNotif : e.hotbarNotif; }
    private static int chIndex(Notif n) { return n.channel() == 1 ? 1 : 0; }

    static void push(Notif n) {
        LIVE.add(n);
        int same = 0;
        for (int i = LIVE.size() - 1; i >= 0; i--) {
            if (chIndex(LIVE.get(i)) == chIndex(n) && ++same > 3) LIVE.remove(i);
        }
    }

    static void push(String title, String msg, int accentRgb, String icon, int channel, String count, String label) {
        push(new Notif(title, msg, accentRgb & 0xFFFFFF, icon, System.currentTimeMillis(), channel, count, label));
    }

    /** System notice (alerts toggled, tests of the full card). */
    static void push(String title, String msg, int accentRgb, String icon) {
        push(title, msg, accentRgb, icon, -1, null, null);
    }

    static void clear() { LIVE.clear(); }

    // ---------- geometry ----------
    private static int ci(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    static float scale(AlertEngine e) { return ci(e.notifScale, 50, 250) / 100f; }
    /** This notification's card size: its own setting if it has one, otherwise the Style tab size. */
    static float scaleOf(AlertEngine e, Notif n) {
        NotifChannel ch = chOf(e, n);
        return ch != null && ch.sizePct > 0 ? ci(ch.sizePct, 50, 250) / 100f : scale(e);
    }
    /** The channel config for a real alert, or null for system notices (which always use the Style settings). */
    static NotifChannel chOf(AlertEngine e, Notif n) { return n.channel() < 0 ? null : channelOf(e, n.channel()); }
    static long lifeOf(AlertEngine e, Notif n) {
        NotifChannel ch = chOf(e, n);
        return ch != null && ch.seconds > 0 ? ci(ch.seconds, 1, 30) * 1000L : lifeMs(e);
    }
    static int animOf(AlertEngine e, Notif n) {
        NotifChannel ch = chOf(e, n);
        return ci(ch != null && ch.anim >= 0 ? ch.anim : e.notifAnim, 0, 3);
    }
    static float tscale(AlertEngine e) { return ci(e.textScale, 50, 250) / 100f; }
    static long lifeMs(AlertEngine e) { return ci(e.notifSeconds, 1, 30) * 1000L; }

    /** Long custom messages are cut so a card never grows wider than the screen. */
    static String fit(TextRenderer tr, String s) {
        if (s == null) return "";
        if (tr.getWidth(s) <= 300) return s;
        return tr.trimToWidth(s, 290) + "...";
    }

    private static boolean hasIcon(Notif n) { return n.icon() != null; }

    /** Mode actually drawn: system notices are always full; icon-only without an icon falls back to icon + count. */
    static int effMode(AlertEngine e, Notif n) {
        if (n.count() == null) return 3;
        int m = ci(channelOf(e, n.channel()).mode, 0, 3);
        if (m == 0 && !hasIcon(n)) m = 1;
        return m;
    }

    private static boolean iconShown(AlertEngine e, Notif n, int mode) {
        NotifChannel ch = chOf(e, n);
        return hasIcon(n) && (mode == 0 || (e.notifIcon && (ch == null || ch.showIcon)));
    }

    static int cardW(TextRenderer tr, Notif n, AlertEngine e) {
        int mode = effMode(e, n);
        boolean icon = iconShown(e, n, mode);
        float ts = tscale(e);
        if (mode == 0) return 12 + 16 + 12;
        int w = 12 + (icon ? 22 : 0);
        if (mode == 3) {
            w += Math.round(Math.max(tr.getWidth(fit(tr, n.title())), tr.getWidth(fit(tr, n.msg()))) * ts);
        } else {
            w += Math.round(tr.getWidth(n.count()) * ts);
            if (mode == 2 && n.label() != null) w += 8 + Math.round(tr.getWidth(n.label()) * ts);
        }
        return w + 12;
    }

    static int cardH(AlertEngine e, Notif n) {
        float ts = tscale(e);
        if (effMode(e, n) == 3) return Math.max(26, Math.round(11 + 19 * ts));
        return Math.max(24, Math.round(8 * ts) + 16);
    }

    // ---------- drawing ----------
    private static void drawCard(DrawContext c, TextRenderer tr, Notif n, AlertEngine e, int w, int h, float a) {
        int accent = n.accent(), bg = e.colBg & 0xFFFFFF, border = e.colBorder & 0xFFFFFF, txt = e.colText & 0xFFFFFF;
        float ts = tscale(e);
        int mode = effMode(e, n);
        boolean icon = iconShown(e, n, mode);
        Gfx.rr(c, -1, -1, w + 1, h + 1, 7, Gfx.al(0xFF000000 | border, a));
        Gfx.grad(c, 0, 0, w, h, 6, bg, Gfx.mix(bg, accent, 0.22f), a, 1.6);
        Gfx.rr(c, 3, 5, 6, h - 5, 1, Gfx.al(0xFF000000 | accent, a));
        int tx = 12;
        if (icon) {
            if (a > 0.5f) Icons.draw(c, n.icon(), 12, (h - 16) / 2);
            tx += mode == 0 ? 16 : 22;
        }
        if (mode == 3) {
            int ty = Math.max(3, Math.round((h - (11 + 8) * ts) / 2f));
            Gfx.text(c, tr, fit(tr, n.title()), tx, ty, accent, a, ts);
            Gfx.text(c, tr, fit(tr, n.msg()), tx, ty + Math.round(11 * ts), txt, a, ts);
        } else if (mode >= 1) {
            int ty = Math.round((h - 8 * ts) / 2f);
            Gfx.text(c, tr, n.count(), tx, ty, accent, a, ts);
            tx += Math.round(tr.getWidth(n.count()) * ts);
            if (mode == 2 && n.label() != null) Gfx.text(c, tr, n.label(), tx + 8, ty, txt, a, ts);
        }
    }

    /**
     * Draws a card whose final top-left corner is (x, y) at size scale s, at the given point in its life.
     * age/life in ms. fromTop picks the slide direction. mul scales opacity (used to dim disabled previews).
     * Animations: 0 fade, 1 slide, 2 pop, 3 minimal.
     */
    static void drawAnimated(DrawContext c, TextRenderer tr, Notif n, AlertEngine e, int x, int y, float s,
                             long age, long life, boolean fromTop, float mul) {
        int anim = animOf(e, n);
        long inMs = anim == 1 ? 280 : anim == 2 ? 300 : anim == 3 ? 80 : 250;
        long outMs = anim == 1 ? 300 : anim == 2 ? 200 : anim == 3 ? 120 : 300;
        float in = Math.max(0f, Math.min(1f, age / (float) inMs));
        float out = Math.max(0f, Math.min(1f, (life - age) / (float) outMs));
        float ein = 1f - (1f - in) * (1f - in) * (1f - in);
        float p = Math.min(ein, out);
        float base = (ci(e.notifAlpha, 10, 100) / 100f) * mul;
        float alpha = p * base;
        int w = cardW(tr, n, e), h = cardH(e, n);
        float f = s, offY = 0f;
        if (anim == 1) offY = (fromTop ? -1f : 1f) * (1f - p) * (h * s + 12f);
        if (anim == 2) {
            float t = in - 1f;
            float back = 1f + 2.70158f * t * t * t + 1.70158f * t * t;   // ease-out-back (slight overshoot)
            f = s * back * out;
            alpha = Math.min(1f, in * 2.5f) * out * base;
        }
        if (alpha < 0.03f || f < 0.05f) return;
        float cx = x + w * s / 2f, cy = y + h * s / 2f + offY;
        Gfx.push(c);
        Gfx.translate(c, cx, cy);
        Gfx.scale(c, f);
        Gfx.translate(c, -w / 2f, -h / 2f);
        drawCard(c, tr, n, e, w, h, alpha);
        Gfx.pop(c);
    }

    /** Fully visible, no animation (used by the mover screen). */
    static void drawStatic(DrawContext c, TextRenderer tr, Notif n, AlertEngine e, int x, int y, float mul) {
        drawAnimated(c, tr, n, e, x, y, scaleOf(e, n), 100_000, 1_000_000, true, mul);
    }

    static void render(DrawContext c, AlertEngine e) {
        if (LIVE.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        if (tr == null || mc.getWindow() == null) return;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        long now = System.currentTimeMillis();
        LIVE.removeIf(n -> now - n.born() > lifeOf(e, n));
        for (int ch = 0; ch < 2; ch++) {
            NotifChannel cfg = ch == 1 ? e.lowNotif : e.hotbarNotif;
            boolean up = cfg.y > 0.6;           // stack upward when the cards sit in the lower part of the screen
            boolean fromTop = cfg.y <= 0.5;
            int idx = 0;
            for (Notif n : new ArrayList<>(LIVE)) {
                if (chIndex(n) != ch) continue;
                float s = scaleOf(e, n); long life = lifeOf(e, n);
                int w = cardW(tr, n, e), h = cardH(e, n);
                int step = Math.round((h + 4) * s);
                int x = (int) Math.round(cfg.x * Math.max(0, sw - w * s));
                int y = (int) Math.round(cfg.y * Math.max(0, sh - h * s)) + (up ? -idx : idx) * step;
                drawAnimated(c, tr, n, e, x, y, s, now - n.born(), life, fromTop, 1f);
                idx++;
            }
        }
    }
}
