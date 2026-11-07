package dev.restockmanager.client;

import dev.restockmanager.core.AlertEngine;
import dev.restockmanager.core.NotifChannel;
import dev.restockmanager.core.TrackedRule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Pop-up settings window: dark panel with a soft blue glow rising from the bottom.
 * Tabs: General, Hotbar Alerts, Low Kit Supplies, Style, Import/Export. Numbers are typed in. Changes save immediately.
 */
public class SettingsScreen extends Screen {
    private static final int ACCENT = 0xFF36A9E8, TEXT = 0xFFF2F6FA, MUTED = 0xFF8590A6, SIDE_TXT = 0xFFB9C3D6,
            OFFTRACK = 0xFF2B2B35, BTN = 0xFF1C1C26, BTN_HOV = 0xFF2C2C3A, BOX = 0xFF0E0E16, BOX_LINE = 0xFF2E2E3C,
            LINE = 0x26FFFFFF, CARD = 0x0FFFFFFF, WHITE = 0xFFFFFFFF;
    private static final String[] ANIM_NAMES = {"Fade", "Slide", "Pop", "Minimal"};
    private enum Tab { GENERAL, HOTBAR, LOW, NOTIFS, STYLE, SHARE }
    private enum Style { SWITCH, TAB, ITEM, SMALL }
    private record Lbl(int x, int baseY, int w, Supplier<String> text, int color) { }
    private record Fld(TextFieldWidget f, int baseY, int boxX, int boxBaseY, int boxW, int boxH) { }
    private record Swatch(int x, int baseY, IntSupplier rgb) { }
    private record Ico(int x, int baseY, String icon) { }

    private final AlertEngine engine;
    private final long openedAt = System.currentTimeMillis();
    private long previewStart = System.currentTimeMillis();
    private Tab tab = Tab.GENERAL;
    private int selHotbar = 0, selLow = 0;
    private String status = "Importing a code replaces your current settings.";
    private String profileName = "my-kit";

    private final List<Flat> all = new ArrayList<>();
    private final List<Lbl> labels = new ArrayList<>();
    private final List<Fld> fields = new ArrayList<>();
    private final List<Swatch> swatches = new ArrayList<>();
    private final List<Ico> icons = new ArrayList<>();
    private String headerIcon;
    private int px, py, pw, ph, sbW, contentL, contentR, bodyTop, bodyBottom;
    private float fade = 1f;
    private int dy = 0;

    public SettingsScreen(AlertEngine engine) {
        super(Text.literal("Restock Manager"));
        this.engine = engine;
    }

    // ---------- drawing helpers ----------
    private static net.minecraft.text.Text lit(String s) { return net.minecraft.text.Text.literal(s); }
    private static int al(int argb, float a) { return Gfx.al(argb, a); }
    private static void rr(DrawContext c, int x1, int y1, int x2, int y2, int r, int col) { Gfx.rr(c, x1, y1, x2, y2, r, col); }

    // ---------- custom flat button (inherits vanilla click handling) ----------
    private final class Flat extends ButtonWidget {
        final Style style; final int baseY; final String label; final BooleanSupplier on;
        boolean selected; float knob; String icon;

        Flat(Style style, int x, int y, int w, int h, String label, BooleanSupplier on, Runnable press) {
            super(x, y, w, h, lit(label), b -> press.run(), DEFAULT_NARRATION_SUPPLIER);
            this.style = style; this.baseY = y; this.label = label; this.on = on;
            this.knob = (on != null && on.getAsBoolean()) ? 1f : 0f;
            this.setAlpha(0f); // hides vanilla button texture; we draw everything ourselves
        }

        public void drawIcon(DrawContext c, int mx, int my, float delta) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hov = isHovered();
            int ty = y + (h - 8) / 2;
            switch (style) {
                case SWITCH -> {
                    boolean v = on.getAsBoolean();
                    knob += ((v ? 1f : 0f) - knob) * 0.4f;
                    int track = v ? (hov ? 0xFF5BBDF2 : ACCENT) : (hov ? 0xFF3A3A47 : OFFTRACK);
                    rr(c, x, y, x + w, y + h, h / 2, al(track, fade));
                    int kx = x + 2 + Math.round((w - h) * knob);
                    rr(c, kx, y + 2, kx + h - 4, y + h - 2, (h - 4) / 2, al(WHITE, fade));
                }
                case TAB -> {
                    if (hov && !selected) rr(c, x, y, x + w, y + h, 4, al(0x14FFFFFF, fade));
                    c.drawText(tr, lit(label), x + (w - tr.getWidth(label)) / 2, ty, al(selected ? TEXT : MUTED, fade), false);
                    if (selected) c.fill(x + 6, y + h - 2, x + w - 6, y + h, al(ACCENT, fade));
                }
                case ITEM -> {
                    if (selected) {
                        rr(c, x, y, x + w, y + h, 5, al(0x2236A9E8, fade));
                        c.fill(x, y + 4, x + 2, y + h - 4, al(ACCENT, fade));
                    } else if (hov) rr(c, x, y, x + w, y + h, 5, al(0x12FFFFFF, fade));
                    if (icon != null && fade > 0.6f) Icons.draw(c, icon, x + 5, y + (h - 16) / 2);
                    c.drawText(tr, lit(label), x + (icon != null ? 26 : 9), ty, al(selected ? TEXT : SIDE_TXT, fade), false);
                }
                case SMALL -> {
                    rr(c, x, y, x + w, y + h, 5, al(hov ? BTN_HOV : BTN, fade));
                    c.drawText(tr, lit(label), x + (w - tr.getWidth(label)) / 2, ty, al(TEXT, fade), false);
                }
            }
        }
    }

    private Flat add(Flat f) { all.add(f); addDrawableChild(f); return f; }

    // ---------- layout ----------
    @Override
    protected void init() {
        all.clear(); labels.clear(); fields.clear(); swatches.clear(); icons.clear(); headerIcon = null;
        TextRenderer tr = this.textRenderer;
        pw = Math.min(this.width - 24, 640);
        ph = Math.min(this.height - 24, 330);
        px = (this.width - pw) / 2; py = (this.height - ph) / 2;
        bodyTop = py + 38; bodyBottom = py + ph - 10;
        sbW = (tab == Tab.HOTBAR || tab == Tab.LOW) ? 184 : 0;

        // tabs shrink their padding on narrow screens so they always fit
        String[] names = {"General", "Hotbar Alerts", "Low Kit", "Notifications", "Style", "Import/Export"};
        Tab[] tabs = Tab.values();
        int titleEnd = px + 18 + tr.getWidth("Restock Manager") + 14;
        int textSum = 0;
        for (String n : names) textSum += tr.getWidth(n);
        int avail = (px + pw - 40) - titleEnd;
        int pad = Math.max(8, Math.min(20, (avail - textSum - 2 * (names.length - 1)) / names.length));
        int tx = titleEnd;
        for (int i = 0; i < names.length; i++) {
            final Tab t = tabs[i];
            int w = tr.getWidth(names[i]) + pad;
            Flat b = add(new Flat(Style.TAB, tx, py + 9, w, 22, names[i], null, () -> { tab = t; clearAndInit(); }));
            b.selected = (tab == t);
            tx += w + 2;
        }
        add(new Flat(Style.SMALL, px + pw - 30, py + 12, 18, 16, "x", null, this::close));

        contentL = px + (sbW > 0 ? sbW + 28 : 26);
        contentR = px + pw - 22;

        switch (tab) {
            case GENERAL -> buildGeneral();
            case NOTIFS -> buildNotifs();
            case STYLE -> buildStyle();
            case SHARE -> buildShare();
            default -> buildItems();
        }
    }

    // ----- General -----
    private void buildGeneral() {
        int y = bodyTop + 14;
        switchRow(y, "Alerts", "Master switch for every notification.", () -> engine.globalEnabled, v -> engine.globalEnabled = v);
        switchRow(y + 34, "Count offhand items", "Include your offhand in total supply counts.", () -> engine.countOffhand, v -> engine.countOffhand = v);
        switchRow(y + 68, "\"Kit Restocked!\" notice", "Tell me when a low item jumps back up (regear).", () -> engine.restockedNotice, v -> engine.restockedNotice = v);
        switchRow(y + 102, "Same sound for every item", "Use one sound for all alerts instead of each item's own.", () -> engine.useGlobalSound, v -> engine.useGlobalSound = v);
        soundRow(y + 136, "Shared sound", () -> engine.globalSound, v -> engine.globalSound = v, () -> 1f);
        numberRowIn(contentL, contentR, y + 164, "Notification time (seconds)", () -> engine.notifSeconds, v -> engine.notifSeconds = Math.max(1, v), 30, "");
        labels.add(new Lbl(contentL, y + 196, 0, () -> "This mod only shows reminders. It never moves items or sends /rg.", MUTED));
        labels.add(new Lbl(contentL, y + 208, 0, () -> "Open this menu with /rsm or a key bound in Options > Controls (there is also a Toggle Alerts key).", MUTED));
    }

    // ----- Items -----
    private List<TrackedRule> rules() { return tab == Tab.HOTBAR ? engine.hotbarRules : engine.lowKitRules; }
    private int sel() { return tab == Tab.HOTBAR ? selHotbar : selLow; }

    private void buildItems() {
        List<TrackedRule> rs = rules();
        int n = Math.max(1, rs.size());
        int avail = (bodyBottom - 24) - (bodyTop + 6);
        int rowH = Math.min(26, avail / n);
        for (int i = 0; i < rs.size(); i++) {
            final int idx = i; final TrackedRule r = rs.get(i);
            int y = bodyTop + 6 + i * rowH;
            Flat row = add(new Flat(Style.ITEM, px + 12, y, sbW - 44, rowH - 2, r.displayName, null, () -> {
                if (tab == Tab.HOTBAR) selHotbar = idx; else selLow = idx; clearAndInit(); }));
            row.selected = (i == clamp(sel(), rs.size()));
            row.icon = Icons.of(r);
            add(new Flat(Style.SWITCH, px + 12 + sbW - 36, y + (rowH - 2 - 12) / 2, 22, 12, "", () -> r.enabled,
                    () -> { r.enabled = !r.enabled; save(); }));
        }
        add(new Flat(Style.SMALL, px + 12, bodyBottom - 20, sbW - 8, 18, "Reset to defaults", null, this::resetAll));

        if (rs.isEmpty()) return;
        final TrackedRule r = rs.get(clamp(sel(), rs.size()));
        int y = bodyTop + 12;
        headerIcon = Icons.of(r);
        int hx = headerIcon != null ? contentL + 22 : contentL;
        labels.add(new Lbl(hx, y, 0, () -> r.displayName, TEXT));
        labels.add(new Lbl(hx, y + 12, 0, () -> tab == Tab.HOTBAR
                ? "Alerts when this runs low in your hotbar and you have spares."
                : "Alerts when your total supply is low. Use /rg yourself.", MUTED));
        add(new Flat(Style.SWITCH, contentR - 22, y + 2, 22, 12, "", () -> r.enabled, () -> { r.enabled = !r.enabled; save(); }));

        int ry = y + 36;
        numberRowIn(contentL, contentR, ry, "Alert threshold", () -> r.threshold, v -> r.threshold = v, 9999, "");
        switchRowIn(contentL, contentR, ry + 26, "Count stacks instead of items", () -> r.countStacks, () -> r.countStacks = !r.countStacks);
        numberRowIn(contentL, contentR, ry + 52, "Cooldown (seconds)", () -> (int) (r.cooldownMs / 1000), v -> r.cooldownMs = v * 1000L, 3600, "s");
        switchRowIn(contentL, contentR, ry + 78, "Sound", () -> r.soundEnabled, () -> r.soundEnabled = !r.soundEnabled);
        soundRow(ry + 104, "Sound effect", () -> r.sound, v -> r.sound = v, () -> r.volume);
        numberRowIn(contentL, contentR, ry + 130, "Volume", () -> Math.round(r.volume * 100), v -> r.volume = v / 100f, 100, "%");

        labels.add(new Lbl(contentL, ry + 158, 0, () -> "Custom message  ({item} {count} {unit})", TEXT));
        makeField(contentL, ry + 169, contentR - contentL, 18, r.message == null ? "" : r.message, 120, false, s -> { r.message = s; save(); });
    }

    // ----- Notifications -----
    private int notifSel = 0;   // 0 = hotbar notification, 1 = /rg notification
    private static final String[] ANIM_CH = {"Style default", "Fade", "Slide", "Pop", "Minimal"};

    private void buildNotifs() {
        int y = bodyTop + 12;
        int bw = (contentR - contentL - 4) / 2;
        Flat t0 = add(new Flat(Style.TAB, contentL, y, bw, 20, "Restock hotbar", null, () -> { notifSel = 0; clearAndInit(); }));
        Flat t1 = add(new Flat(Style.TAB, contentL + bw + 4, y, bw, 20, "/rg reminder", null, () -> { notifSel = 1; clearAndInit(); }));
        t0.selected = notifSel == 0; t1.selected = notifSel == 1;
        NotifChannel ch = notifSel == 0 ? engine.hotbarNotif : engine.lowNotif;
        String desc = notifSel == 0 ? "Shown when an item runs low in your hotbar and you have spares in your inventory."
                                    : "Shown when your total supply runs low. Also used for \"Kit restocked\".";
        int r = y + 28;
        labels.add(new Lbl(contentL, r, 0, () -> desc, MUTED));
        r += 20;
        switchRowIn(contentL, contentR, r, "Show this notification", () -> ch.enabled, () -> ch.enabled = !ch.enabled);
        labels.add(new Lbl(contentL, r + 29, 0, () -> "Shows", TEXT));
        add(new Flat(Style.SMALL, contentR - 170, r + 24, 170, 18, Notifications.MODE_NAMES[Math.max(0, Math.min(3, ch.mode))], null,
                () -> { ch.mode = (ch.mode + 1) % 4; save(); clearAndInit(); }));
        switchRowIn(contentL, contentR, r + 50, "Show the item icon", () -> ch.showIcon, () -> ch.showIcon = !ch.showIcon);
        switchRowIn(contentL, contentR, r + 74, "Play a sound with it", () -> ch.soundOn, () -> ch.soundOn = !ch.soundOn);
        numberRowIn(contentL, contentR, r + 98, "Card size (0 = Style tab)", () -> ch.sizePct, v -> ch.sizePct = v == 0 ? 0 : Math.max(50, v), 250, "%");
        numberRowIn(contentL, contentR, r + 122, "Seconds on screen (0 = Style tab)", () -> ch.seconds, v -> ch.seconds = v, 30, "s");
        labels.add(new Lbl(contentL, r + 151, 0, () -> "Animation", TEXT));
        add(new Flat(Style.SMALL, contentR - 120, r + 146, 120, 18, ANIM_CH[Math.max(0, Math.min(4, ch.anim + 1))], null,
                () -> { ch.anim = ch.anim >= 3 ? -1 : ch.anim + 1; save(); clearAndInit(); }));
        int by = r + 176;
        add(new Flat(Style.SMALL, contentL, by, 150, 18, "Move notifications", null,
                () -> MinecraftClient.getInstance().setScreen(new NotifMoveScreen(this, engine))));
        add(new Flat(Style.SMALL, contentL + 158, by, 80, 18, "Test it", null, () -> {
            if (notifSel == 0) Notifications.push("Restock your hotbar", "Your Ender Pearls are low in your hotbar! Restock from inventory.",
                    Notifications.accent(engine, Notifications.KIND_HOTBAR), "minecraft:ender_pearl", 0, "x5", "Restock hotbar");
            else Notifications.push("Low on supplies", "You're low on Ender Pearls (1 stack remaining). Consider using /rg.",
                    Notifications.accent(engine, Notifications.KIND_LOW), "minecraft:ender_pearl", 1, "1 stack", "/rg reminder");
        }));
        labels.add(new Lbl(contentL, by + 26, 0, () -> "Use Move notifications to drag each one anywhere. Colours, text size and transparency are on the Style tab.", MUTED));
    }

    // ----- Style -----
    private void buildStyle() {
        int y0 = bodyTop + 14;
        int half = (contentR - contentL - 20) / 2;
        int lL = contentL, lR = contentL + half, rL = contentR - half, rR = contentR;
        labels.add(new Lbl(lL, y0 - 2, 0, () -> "Card", MUTED));
        labels.add(new Lbl(rL, y0 - 2, 0, () -> "Colours (hex)", MUTED));
        int y = y0 + 12;
        numberRowIn(lL, lR, y, "Card size", () -> engine.notifScale, v -> engine.notifScale = v, 250, "%");
        numberRowIn(lL, lR, y + 24, "Text size", () -> engine.textScale, v -> engine.textScale = v, 250, "%");
        numberRowIn(lL, lR, y + 48, "Transparency (opacity)", () -> engine.notifAlpha, v -> engine.notifAlpha = v, 100, "%");
        labels.add(new Lbl(lL, y + 77, 0, () -> "Animation", TEXT));
        add(new Flat(Style.SMALL, lR - 90, y + 72, 90, 18, ANIM_NAMES[Math.max(0, Math.min(3, engine.notifAnim))], null, () -> {
            engine.notifAnim = (engine.notifAnim + 1) % 4; save(); previewStart = System.currentTimeMillis(); clearAndInit(); }));
        switchRowIn(lL, lR, y + 96, "Item icon in notifications", () -> engine.notifIcon, () -> engine.notifIcon = !engine.notifIcon);
        add(new Flat(Style.SMALL, lL, y + 120, 100, 18, "Reset style", null, () -> { engine.resetStyle(); save(); clearAndInit(); }));

        colorRow(rL, rR, y, "Background", () -> engine.colBg, v -> engine.colBg = v);
        colorRow(rL, rR, y + 24, "Border", () -> engine.colBorder, v -> engine.colBorder = v);
        colorRow(rL, rR, y + 48, "Message text", () -> engine.colText, v -> engine.colText = v);
        colorRow(rL, rR, y + 72, "Hotbar alert accent", () -> engine.colHotbar, v -> engine.colHotbar = v);
        colorRow(rL, rR, y + 96, "Low supplies accent", () -> engine.colLow, v -> engine.colLow = v);
        colorRow(rL, rR, y + 120, "Restocked accent", () -> engine.colOk, v -> engine.colOk = v);
        labels.add(new Lbl(contentL, y + 148, 0, () -> "Preview", MUTED));
    }

    private void colorRow(int L, int R, int y, String title, IntSupplier get, IntConsumer set) {
        labels.add(new Lbl(L, y + 5, 0, () -> title, TEXT));
        int boxW = 62, boxX = R - boxW;
        swatches.add(new Swatch(boxX - 18, y + 3, get));
        makeField(boxX, y - 1, boxW, 18, String.format("%06X", get.getAsInt() & 0xFFFFFF), 6, false, s -> {
            if (s.length() == 6) try { set.accept(Integer.parseInt(s, 16)); save(); } catch (NumberFormatException ignored) { }
        }, s -> s.matches("[0-9a-fA-F]{0,6}"));
    }

    // ----- Import / Export -----
    private void buildShare() {
        int y = bodyTop + 14;
        labels.add(new Lbl(contentL, y, 0, () -> "Share code", TEXT));
        labels.add(new Lbl(contentL, y + 12, 0, () -> "Copy a code to send to a friend, or copy one you were sent and import it.", MUTED));
        add(new Flat(Style.SMALL, contentL, y + 28, 150, 18, "Copy my share code", null, this::copyCode));
        add(new Flat(Style.SMALL, contentL + 158, y + 28, 190, 18, "Import code from clipboard", null, this::importCode));
        labels.add(new Lbl(contentL, y + 54, 0, () -> status, ACCENT));

        labels.add(new Lbl(contentL, y + 82, 0, () -> "Save to a file", TEXT));
        labels.add(new Lbl(contentL, y + 94, 0, () -> "Saves all your current settings under a name. Load it later, or send the file to a friend.", MUTED));
        makeField(contentL, y + 108, 200, 18, profileName, 32, false, s -> profileName = s);
        add(new Flat(Style.SMALL, contentL + 208, y + 108, 110, 18, "Save to file", null, this::saveFile));

        labels.add(new Lbl(contentL, y + 136, 0, () -> "Saved files", TEXT));
        List<String> names = ConfigStore.listProfiles();
        int ry = y + 150;
        for (int i = 0; i < Math.min(names.size(), 5); i++) {
            final String nm = names.get(i);
            labels.add(new Lbl(contentL + 4, ry + 5, 0, () -> nm, SIDE_TXT));
            add(new Flat(Style.SMALL, contentR - 60, ry, 60, 16, "Load", null, () -> loadFile(nm)));
            ry += 20;
        }
        if (names.isEmpty()) labels.add(new Lbl(contentL + 4, ry + 5, 0, () -> "No saved files yet.", MUTED));
        labels.add(new Lbl(contentL, bodyBottom - 12, 0, () -> "Files are kept in .minecraft/config/restockmanager-profiles", MUTED));
    }

    private void copyCode() {
        try {
            MinecraftClient.getInstance().keyboard.setClipboard(ConfigStore.shareCode(engine));
            status = "Copied! Paste it to a friend.";
        } catch (Exception e) { status = "Couldn't copy the code."; }
    }

    private void importCode() {
        try {
            String clip = MinecraftClient.getInstance().keyboard.getClipboard();
            ConfigStore.importShareCode(engine, clip);
            status = "Imported. Your settings were replaced.";
            afterImport();
        } catch (Exception e) {
            status = "That isn't a valid Restock Manager code.";
        }
    }

    private void saveFile() {
        try {
            String n = ConfigStore.cleanName(profileName);
            ConfigStore.saveProfile(engine, n);
            profileName = n;
            status = "Saved \"" + n + "\".";
            clearAndInit();
        } catch (Exception e) { status = "Couldn't save the file."; }
    }

    private void loadFile(String name) {
        try {
            ConfigStore.loadProfile(engine, name);
            status = "Loaded \"" + name + "\".";
            afterImport();
        } catch (Exception e) { status = "Couldn't load \"" + name + "\" (not a valid file)."; }
    }

    private void afterImport() {
        selHotbar = 0; selLow = 0;
        Notifications.clear();
        clearAndInit();
    }

    // ----- shared row builders -----
    private void switchRow(int y, String title, String desc, BooleanSupplier g, Consumer<Boolean> s) {
        labels.add(new Lbl(contentL, y, 0, () -> title, TEXT));
        labels.add(new Lbl(contentL, y + 12, 0, () -> desc, MUTED));
        add(new Flat(Style.SWITCH, contentR - 22, y + 3, 22, 12, "", g, () -> { s.accept(!g.getAsBoolean()); save(); }));
    }

    private void switchRowIn(int L, int R, int y, String title, BooleanSupplier g, Runnable toggle) {
        labels.add(new Lbl(L, y + 5, 0, () -> title, TEXT));
        add(new Flat(Style.SWITCH, R - 22, y + 3, 22, 12, "", g, () -> { toggle.run(); save(); }));
    }

    /** A labelled box you click and type a whole number into. */
    private void numberRowIn(int L, int R, int y, String title, IntSupplier get, IntConsumer set, int max, String suffix) {
        labels.add(new Lbl(L, y + 5, 0, () -> title, TEXT));
        int bw = 52;
        makeField(R - 18 - bw, y - 1, bw, 18, String.valueOf(get.getAsInt()), String.valueOf(max).length(), true, s -> {
            if (s.isEmpty()) return;
            try { set.accept(Math.max(0, Math.min(max, Integer.parseInt(s)))); save(); } catch (NumberFormatException ignored) { }
        });
        if (!suffix.isEmpty()) labels.add(new Lbl(R - 12, y + 5, 0, () -> suffix, MUTED));
    }

    /** Sound picker: previous / name / next, and a Play button. Changing the sound plays it once. */
    private void soundRow(int y, String title, Supplier<String> get, Consumer<String> set, Supplier<Float> vol) {
        labels.add(new Lbl(contentL, y + 4, 0, () -> title, TEXT));
        int R = contentR;
        add(new Flat(Style.SMALL, R - 198, y, 18, 16, "<", null, () -> cycleSound(get, set, vol, -1)));
        labels.add(new Lbl(R - 176, y + 4, 112, () -> SoundList.nameOf(get.get()), TEXT));
        add(new Flat(Style.SMALL, R - 60, y, 18, 16, ">", null, () -> cycleSound(get, set, vol, 1)));
        add(new Flat(Style.SMALL, R - 38, y, 38, 16, "Play", null, () -> playPreview(get.get(), vol.get())));
    }

    private void cycleSound(Supplier<String> get, Consumer<String> set, Supplier<Float> vol, int d) {
        int n = SoundList.ALL.size();
        int i = SoundList.indexOf(get.get());
        i = i < 0 ? (d > 0 ? 0 : n - 1) : (i + d + n) % n;
        String id = SoundList.ALL.get(i).id();
        set.accept(id);
        save();
        playPreview(id, vol.get());
    }

    private void playPreview(String id, float vol) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Identifier sid = id == null ? null : Identifier.tryParse(id);
            if (mc.player == null || sid == null) return;
            SoundEvent se = Registries.SOUND_EVENT.get(sid);
            if (se != null) mc.player.playSound(se, Math.max(0.1f, Math.min(1f, vol)), 1.0f);
        } catch (Throwable ignored) { }
    }

    private void makeField(int boxX, int boxTop, int boxW, int boxH, String initial, int maxLen, boolean numeric, Consumer<String> onChange) {
        makeField(boxX, boxTop, boxW, boxH, initial, maxLen, numeric, onChange, numeric ? s -> s.matches("\\d*") : null);
    }

    private void makeField(int boxX, int boxTop, int boxW, int boxH, String initial, int maxLen, boolean numeric,
                           Consumer<String> onChange, java.util.function.Predicate<String> allowed) {
        int fy = boxTop + (boxH - 8) / 2;
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, boxX + 6, fy, boxW - 12, 10, Text.literal(""));
        f.setDrawsBackground(false);
        f.setEditableColor(0xFFF2F6FA);
        f.setMaxLength(maxLen);
        if (allowed != null) f.setTextPredicate(allowed);
        f.setText(initial);
        f.setChangedListener(onChange);
        addDrawableChild(f);
        fields.add(new Fld(f, fy, boxX, boxTop, boxW, boxH));
    }

    private static int clamp(int i, int n) { return Math.max(0, Math.min(i, Math.max(0, n - 1))); }

    private void save() { ConfigStore.save(engine); }

    private void resetAll() {
        engine.hotbarRules.clear(); engine.lowKitRules.clear();
        Defaults.populate(engine);
        engine.globalEnabled = true; engine.countOffhand = true; engine.restockedNotice = true; engine.regearMinIncrease = 16;
        engine.reset();
        save();
        clearAndInit();
    }

    // ---------- rendering ----------
    /** Intentionally empty: we draw our own backdrop in render(). */
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 200f);
        float e = 1f - (1f - t) * (1f - t) * (1f - t);
        fade = e;
        dy = Math.round((1f - e) * 12);
        for (Flat f : all) f.setY(f.baseY + dy);
        for (Fld fl : fields) { fl.f().setY(fl.baseY() + dy); fl.f().visible = e > 0.9f; }
        if (e < 0.04f) return;   // text with near-zero alpha would be drawn opaque

        c.fill(0, 0, this.width, this.height, al(0x66000000, e));
        rr(c, px - 1, py - 1 + dy, px + pw + 1, py + ph + 1 + dy, 10, al(0xFF23232F, e));
        Gfx.grad(c, px, py + dy, px + pw, py + ph + dy, 9, 0x000000, 0x161C42, e, 2.4);
        c.drawText(this.textRenderer, Text.literal("Restock Manager"), px + 18, py + 16 + dy, al(TEXT, e), false);
        c.fill(px + 10, py + 38 + dy, px + pw - 10, py + 39 + dy, al(LINE, e));

        if (sbW > 0) {
            rr(c, px + 8, bodyTop + dy, px + 8 + sbW, bodyBottom + dy, 7, al(CARD, e));
            rr(c, px + sbW + 16, bodyTop + dy, px + pw - 8, bodyBottom + dy, 7, al(CARD, e));
        } else {
            rr(c, px + 8, bodyTop + dy, px + pw - 8, bodyBottom + dy, 7, al(CARD, e));
        }
        for (Fld fl : fields) {
            boolean foc = fl.f().isFocused();
            rr(c, fl.boxX(), fl.boxBaseY() + dy, fl.boxX() + fl.boxW(), fl.boxBaseY() + fl.boxH() + dy, 5, al(foc ? ACCENT : BOX_LINE, e));
            rr(c, fl.boxX() + 1, fl.boxBaseY() + 1 + dy, fl.boxX() + fl.boxW() - 1, fl.boxBaseY() + fl.boxH() - 1 + dy, 4, al(BOX, e));
        }
        for (Swatch sw : swatches) {
            rr(c, sw.x() - 1, sw.baseY() - 1 + dy, sw.x() + 13, sw.baseY() + 13 + dy, 3, al(0xFF5A5A6C, e));
            rr(c, sw.x(), sw.baseY() + dy, sw.x() + 12, sw.baseY() + 12 + dy, 3, al(0xFF000000 | (sw.rgb().getAsInt() & 0xFFFFFF), e));
        }

        super.render(c, mouseX, mouseY, delta);

        if (headerIcon != null && fade > 0.6f) Icons.draw(c, headerIcon, contentL, bodyTop + 9 + dy);
        for (Ico ic : icons) if (fade > 0.6f) Icons.draw(c, ic.icon(), ic.x(), ic.baseY() + dy);
        for (Lbl l : labels) {
            String s = l.text().get();
            if (l.w() > 0) {
                int x = l.x() + (l.w() - this.textRenderer.getWidth(s)) / 2;
                c.drawText(this.textRenderer, Text.literal(s), x, l.baseY() + dy, al(l.color(), e), false);
                continue;
            }
            int limit = Math.max(l.x() + 90, contentR - 24);   // keep text inside the window and clear of switches
            int ly = l.baseY() + dy;
            for (String line : wrap(s, limit - l.x())) {
                c.drawText(this.textRenderer, Text.literal(line), l.x(), ly, al(l.color(), e), false);
                ly += 10;
            }
        }
        if (tab == Tab.STYLE) drawStylePreview(c, e);
        else if (tab == Tab.HOTBAR || tab == Tab.LOW) drawPreview(c, e);
    }

    /** Word-wraps text to a pixel width (max 3 lines, then "..."). */
    private List<String> wrap(String s, int maxW) {
        TextRenderer tr = this.textRenderer;
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty() || tr.getWidth(s) <= maxW) { out.add(s == null ? "" : s); return out; }
        StringBuilder cur = new StringBuilder();
        for (String w : s.split(" ")) {
            String t = cur.length() == 0 ? w : cur + " " + w;
            if (cur.length() == 0 || tr.getWidth(t) <= maxW) cur = new StringBuilder(t);
            else { out.add(cur.toString()); cur = new StringBuilder(w); }
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.size() > 3) {
            out = new ArrayList<>(out.subList(0, 3));
            out.set(2, tr.trimToWidth(out.get(2), Math.max(10, maxW - 14)) + "...");
        }
        return out;
    }

    /** Loops the real notification card (with the chosen animation) so you can see every setting. */
    private void drawStylePreview(DrawContext c, float e) {
        if (e < 0.9f) return;
        TextRenderer tr = this.textRenderer;
        int top = bodyTop + 14 + 12 + 148 + 12, bottom = bodyBottom - 4;
        long life = Notifications.lifeMs(engine);
        long period = life + 900;
        long since = System.currentTimeMillis() - previewStart;
        int kind = (int) ((since / period) % 3);
        long age = since % period;
        String[] titles = {"Restock your hotbar", "Low on supplies", "Kit restocked"};
        String[] msgs = {"Your Ender Pearls are low in your hotbar! Restock from inventory.",
                "You're low on Ender Pearls (1 stack remaining). Consider using /rg.",
                "Kit Restocked! Ender Pearls: 64"};
        Notifications.Notif n = new Notifications.Notif(titles[kind], msgs[kind], Notifications.accent(engine, kind), "minecraft:ender_pearl", 0L, -1, null, null);
        int natW = Notifications.cardW(tr, n, engine), natH = Notifications.cardH(engine, n);
        float s = Notifications.scale(engine);
        s = Math.min(s, (contentR - contentL) / (float) natW);
        s = Math.min(s, (bottom - top) / (float) natH);
        int y = top + Math.max(0, (bottom - top - Math.round(natH * s)) / 2) + dy;
        c.enableScissor(contentL - 4, top + dy, contentR + 4, bottom + dy);
        Notifications.drawAnimated(c, tr, n, engine, contentL, y, s, age, life, true, 1f);
        c.disableScissor();
    }

    private void drawPreview(DrawContext c, float e) {
        String msg;
        if (rules().isEmpty()) {
            msg = "Your Ender Pearls are low in your hotbar! Restock from inventory.";
        } else {
            TrackedRule r = rules().get(clamp(sel(), rules().size()));
            String fb = tab == Tab.HOTBAR ? "Your {item} are low in your hotbar! Restock from inventory."
                    : "You're low on {item} ({count} {unit} remaining). Consider using /rg.";
            String m = (r.message == null || r.message.isBlank()) ? fb : r.message;
            msg = m.replace("{item}", r.displayName).replace("{count}", String.valueOf(r.threshold))
                    .replace("{unit}", r.countStacks ? "stacks" : "items");
        }
        if (tab == Tab.GENERAL) return;
        int w = Math.min(contentR - contentL, this.textRenderer.getWidth(msg) + 24);
        int x = contentL, y = bodyBottom - 24 + dy;
        rr(c, x, y, x + w, y + 20, 5, al(0xFF2E2E3C, e));
        rr(c, x + 1, y + 1, x + w - 1, y + 19, 4, al(0xFF14141E, e));
        c.fill(x + 1, y + 5, x + 3, y + 15, al(ACCENT, e));
        c.drawText(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(msg, w - 16)), x + 10, y + 6, al(TEXT, e), false);
        c.drawText(this.textRenderer, Text.literal("Preview"), x, y - 11, al(MUTED, e), false);
    }
}
