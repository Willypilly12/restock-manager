package dev.restockmanager.client;

import dev.restockmanager.core.AlertEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Drag each notification wherever you want it; Esc returns to the menu.
 * Switched-off items are shown dimmed so you can still place them. Mouse is polled, so no mouse-event API is needed.
 */
public class NotifMoveScreen extends Screen {
    private final Screen parent;
    private final AlertEngine engine;
    private int dragging = -1, offX, offY;
    private boolean wasDown, started;

    public NotifMoveScreen(Screen parent, AlertEngine engine) {
        super(Text.literal("Move notifications"));
        this.parent = parent; this.engine = engine;
    }

    public void close() { ConfigStore.save(engine); MinecraftClient.getInstance().setScreen(parent); }

    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) { }

    private Notifications.Notif sample(int ch) {
        if (ch == 0) return new Notifications.Notif("Restock your hotbar", "Your Ender Pearls are low in your hotbar! Restock from inventory.",
                Notifications.accent(engine, Notifications.KIND_HOTBAR), "minecraft:ender_pearl", 0L, 0, "x5", "Restock hotbar");
        return new Notifications.Notif("Low on supplies", "You're low on Ender Pearls (1 stack remaining). Consider using /rg.",
                Notifications.accent(engine, Notifications.KIND_LOW), "minecraft:ender_pearl", 0L, 1, "1 stack", "/rg reminder");
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        c.fill(0, 0, this.width, this.height, 0x99000000);
        var tr = this.textRenderer;
        int[] x = new int[2], y = new int[2], w = new int[2], h = new int[2];
        double[] fx = {engine.hotbarNotif.x, engine.lowNotif.x};
        double[] fy = {engine.hotbarNotif.y, engine.lowNotif.y};
        for (int i = 0; i < 2; i++) {
            var n = sample(i); float s = Notifications.scaleOf(engine, n);
            w[i] = Math.round(Notifications.cardW(tr, n, engine) * s); h[i] = Math.round(Notifications.cardH(engine, n) * s);
        }

        boolean down = false;
        try {
            long handle = MinecraftClient.getInstance().getWindow().getHandle();
            down = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        } catch (Exception ignored) { }
        if (!started) { wasDown = down; started = true; }   // ignore the click that opened this screen

        for (int i = 0; i < 2; i++) {
            x[i] = (int) Math.round(fx[i] * Math.max(0, this.width - w[i]));
            y[i] = (int) Math.round(fy[i] * Math.max(0, this.height - h[i]));
        }
        if (down && !wasDown && dragging < 0) {
            for (int i = 1; i >= 0; i--) {   // top-most (last drawn) first
                if (mx >= x[i] && mx <= x[i] + w[i] && my >= y[i] && my <= y[i] + h[i]) { dragging = i; offX = mx - x[i]; offY = my - y[i]; break; }
            }
        }
        if (dragging >= 0 && down) {
            int i = dragging;
            double nx = Math.max(0, Math.min(1, (mx - offX) / (double) Math.max(1, this.width - w[i])));
            double ny = Math.max(0, Math.min(1, (my - offY) / (double) Math.max(1, this.height - h[i])));
            if (i == 0) { engine.hotbarNotif.x = nx; engine.hotbarNotif.y = ny; }
            else { engine.lowNotif.x = nx; engine.lowNotif.y = ny; }
            x[i] = (int) Math.round(nx * Math.max(0, this.width - w[i]));
            y[i] = (int) Math.round(ny * Math.max(0, this.height - h[i]));
        }
        if (dragging >= 0 && !down) { dragging = -1; ConfigStore.save(engine); }
        wasDown = down;

        String[] names = {"Restock hotbar notification", "/rg reminder notification"};
        boolean[] on = {engine.hotbarNotif.enabled, engine.lowNotif.enabled};
        for (int i = 0; i < 2; i++) {
            boolean hot = dragging == i || (dragging < 0 && mx >= x[i] && mx <= x[i] + w[i] && my >= y[i] && my <= y[i] + h[i]);
            float mul = on[i] ? 1f : 0.4f;
            if (hot) Gfx.rr(c, x[i] - 3, y[i] - 3, x[i] + w[i] + 3, y[i] + h[i] + 3, 8, Gfx.al(0xFF36A9E8, dragging == i ? 0.9f : 0.45f));
            Notifications.drawStatic(c, tr, sample(i), engine, x[i], y[i], mul);
            String cap = names[i] + (on[i] ? "" : " (off)");
            int cy = y[i] > 12 ? y[i] - 11 : y[i] + h[i] + 3;
            c.drawText(tr, Text.literal(cap), x[i], cy, on[i] ? 0xFFB9C3D6 : 0xFF6D7488, false);
        }
        String hint = "Drag either notification. Press Esc when you're done.";
        c.drawText(tr, Text.literal(hint), (this.width - tr.getWidth(hint)) / 2, this.height / 2, 0xFFF2F6FA, false);
    }
}
