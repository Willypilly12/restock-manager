package dev.restockmanager.core;

/**
 * One of the two notification streams (hotbar restock, /rg reminder).
 * x/y are 0..1 fractions of the free screen area, so the position survives resolution changes.
 * mode: 0 icon only, 1 icon + count, 2 icon + count + short label, 3 full message.
 * sizePct and seconds: 0 means "use the Style tab / General value". anim: -1 means "use the Style tab animation".
 */
public class NotifChannel {
    public boolean enabled = true;
    public double x = 0.5, y = 0.06;
    public int mode = 3;
    public boolean showIcon = true;
    public boolean soundOn = true;
    public int sizePct = 0;
    public int seconds = 0;
    public int anim = -1;

    public NotifChannel() { }
    public NotifChannel(double x, double y) { this.x = x; this.y = y; }

    public void clampValues() {
        x = Double.isNaN(x) ? 0.5 : Math.max(0, Math.min(1, x));
        y = Double.isNaN(y) ? 0.06 : Math.max(0, Math.min(1, y));
        mode = Math.max(0, Math.min(3, mode));
        sizePct = sizePct == 0 ? 0 : Math.max(50, Math.min(250, sizePct));
        seconds = Math.max(0, Math.min(30, seconds));
        anim = Math.max(-1, Math.min(3, anim));
    }

    public void copyFrom(NotifChannel o) {
        enabled = o.enabled; x = o.x; y = o.y; mode = o.mode;
        showIcon = o.showIcon; soundOn = o.soundOn; sizePct = o.sizePct; seconds = o.seconds; anim = o.anim;
        clampValues();
    }
}
